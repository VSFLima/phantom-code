/*
 * Phantom-Code — PTY nativo do motor PRoot (módulo :proot, compila como libpty.so)
 *
 * Por que existe: o Bionic não oferece forkpty(); o terminal do app precisa de
 * um pseudo-terminal REAL para o shell do guest (prompt, eco, cores, job
 * control, ^C). Sem PTY, "sh -c <script>" roda em modo pipe e o guest fica sem
 * interação decente.
 *
 * Fluxo: open(/dev/ptmx) + grantpt + unlockpt + ptsname -> fork ->
 * setsid + TIOCSCTTY + dup2(slave nos std fds) -> execve(argv[0], argv, envp).
 * O par devolvido ao Kotlin é {pid, masterFd}; o master é full-duplex.
 */
#define _GNU_SOURCE
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "phantom-pty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* Copia uma string Java para C; libera com free(). NULL para entrada NULL. */
static char *dup_jstring(JNIEnv *env, jstring s) {
    if (s == NULL) return NULL;
    const char *utf = (*env)->GetStringUTFChars(env, s, NULL);
    if (utf == NULL) return NULL;
    char *out = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, s, utf);
    return out;
}

static void free_vec(char **v) {
    if (v == NULL) return;
    for (size_t i = 0; v[i] != NULL; i++) free(v[i]);
    free(v);
}

static char **to_vec(JNIEnv *env, jobjectArray arr) {
    if (arr == NULL) return NULL;
    jsize n = (*env)->GetArrayLength(env, arr);
    char **v = calloc((size_t) n + 1, sizeof(char *));
    if (v == NULL) return NULL;
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        v[i] = dup_jstring(env, s);
        (*env)->DeleteLocalRef(env, s);
    }
    v[n] = NULL;
    return v;
}

/* Abre o par pty: slave via ptsname em *slave (retorno >= 0), master em *master. */
static int open_pty_pair(int *master) {
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) { LOGE("open(/dev/ptmx): %s", strerror(errno)); return -1; }
    if (grantpt(ptm) != 0) { LOGE("grantpt: %s", strerror(errno)); close(ptm); return -1; }
    if (unlockpt(ptm) != 0) { LOGE("unlockpt: %s", strerror(errno)); close(ptm); return -1; }
    const char *slave_name = ptsname(ptm);
    if (slave_name == NULL) { LOGE("ptsname: %s", strerror(errno)); close(ptm); return -1; }
    int pts = open(slave_name, O_RDWR | O_CLOEXEC);
    if (pts < 0) { LOGE("open(%s): %s", slave_name, strerror(errno)); close(ptm); return -1; }
    *master = ptm;
    return pts;
}

/*
 * spawn(argv, envp, cwd, rows, cols) -> {pid, masterFd} | null
 * Envolve o comando em um PTY real; chamado na thread de IO do app.
 */
JNIEXPORT jintArray JNICALL
Java_com_phantomcode_app_data_vm_PtyNative_spawn(JNIEnv *env, jclass clazz,
        jobjectArray jargv, jobjectArray jenvp, jstring jcwd, jint rows, jint cols) {
    (void) clazz;
    char **argv = to_vec(env, jargv);
    char **envp = to_vec(env, jenvp);
    char *cwd = dup_jstring(env, jcwd);
    int master = -1;
    int slave = open_pty_pair(&master);
    if (argv == NULL || argv[0] == NULL || envp == NULL && jenvp != NULL || slave < 0) {
        if (slave >= 0) { close(slave); close(master); }
        free_vec(argv);
        free_vec(envp);
        free(cwd);
        return NULL;
    }
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork: %s", strerror(errno));
        close(slave);
        close(master);
        free_vec(argv);
        free_vec(envp);
        free(cwd);
        return NULL;
    }
    if (pid == 0) {
        /* Filho: sessão própria (job control) e o slave nos três std fds. */
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);
        close(master);
        if (cwd != NULL && chdir(cwd) != 0) {
            LOGE("chdir(%s): %s", cwd, strerror(errno));
        }
        execve(argv[0], argv, envp);
        LOGE("execve(%s): %s", argv[0], strerror(errno));
        _exit(127);
    }
    /* Pai: fecha o slave, fixa o tamanho inicial e devolve {pid, master}. */
    close(slave);
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ioctl(master, TIOCSWINSZ, &ws);

    jint out[2] = { (jint) pid, (jint) master };
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        kill(pid, SIGKILL);
        close(master);
    } else {
        (*env)->SetIntArrayRegion(env, result, 0, 2, out);
    }
    free_vec(argv);
    free_vec(envp);
    free(cwd);
    return result;
}

/* resize(masterFd, rows, cols) — chamado quando a EmulatorView muda de tamanho. */
JNIEXPORT jint JNICALL
Java_com_phantomcode_app_data_vm_PtyNative_resize(JNIEnv *env, jclass clazz,
        jint master, jint rows, jint cols) {
    (void) env;
    (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    return ioctl((int) master, TIOCSWINSZ, &ws);
}

/* waitFor(pid) — bloqueia até o processo sair; devolve o código (128+sig se sinal). */
JNIEXPORT jint JNICALL
Java_com_phantomcode_app_data_vm_PtyNative_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    pid_t r;
    do {
        r = waitpid((pid_t) pid, &status, 0);
    } while (r < 0 && errno == EINTR);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

/* killTree(pid, sig) — o filho é líder de sessão (setsid): o grupo morre junto. */
JNIEXPORT jint JNICALL
Java_com_phantomcode_app_data_vm_PtyNative_killTree(JNIEnv *env, jclass clazz, jint pid, jint sig) {
    (void) env;
    (void) clazz;
    if (killpg((pid_t) pid, (int) sig) == 0) return 0;
    return kill((pid_t) pid, (int) sig);
}

/* closeFd(fd) — usado pelo Kotlin se precisar liberar o master sem matar o filho. */
JNIEXPORT jint JNICALL
Java_com_phantomcode_app_data_vm_PtyNative_closeFd(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    return close((int) fd);
}
