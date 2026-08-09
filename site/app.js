/* Phantom-Code site — terminal mock, reveal on scroll, contadores, nav. */

const termBody = document.getElementById("termBody");

const FILES = [
  { cmd: "uname -a", out: "Linux phantom 6.1.0-31-arm64 #1 SMP Debian (glibc 2.36)", ok: true },
  { cmd: "python3 --version", out: "Python 3.11.8", ok: true },
  { cmd: "git status", out: "On branch main — nothing to commit, working tree clean", ok: true },
  { cmd: "ollama run llama3", out: "ollama@phantom: ready to serve", ok: true },
];

function typeLine(pre, text, done) {
  const target = pre.querySelector(".typed");
  target.textContent = "";
  const caret = pre.querySelector(".caret");
  let i = 0;
  const timer = setInterval(() => {
    i++;
    target.textContent = text.slice(0, i);
    if (i >= text.length) {
      clearInterval(timer);
      caret.style.display = "none";
      setTimeout(done, 900);
    }
  }, 42);
}

function printOut(entry) {
  const line = document.createElement("pre");
  line.className = "out";
  line.innerHTML = (entry.ok ? '<span class="ok">✓</span> ' : "") + entry.out;
  termBody.appendChild(line);
  termBody.scrollTop = termBody.scrollHeight;
}

function runSequence() {
  if (termBody.dataset.played) return;
  termBody.dataset.played = "1";
  let idx = 0;
  function next() {
    if (idx >= FILES.length) {
      setTimeout(() => {
        const prompt = document.createElement("pre");
        prompt.innerHTML = '<span class="c-green">phantom</span> <span class="c-dim">~</span> <span class="c-purple">$</span> <span class="caret"></span>';
        termBody.appendChild(prompt);
      }, 400);
      return;
    }
    const entry = FILES[idx++];
    const pre = document.createElement("pre");
    pre.innerHTML = '<span class="c-green">phantom</span> <span class="c-dim">~</span> <span class="c-purple">$</span> <span class="typed"></span><span class="caret"></span>';
    termBody.appendChild(pre);
    printOut(entry);
    typeLine(pre, entry.cmd, next);
  }
  next();
}

/* Nav scroll + mobile */
const nav = document.getElementById("nav");
window.addEventListener("scroll", () => {
  nav.classList.toggle("scrolled", window.scrollY > 10);
});
document.getElementById("navToggle").addEventListener("click", () => {
  document.getElementById("navLinks").classList.toggle("open");
});

/* Reveal on scroll */
const revealEls = document.querySelectorAll(".section, .feature, .spec, .phase, .flow__node");
const io = new IntersectionObserver((entries) => {
  entries.forEach((e) => {
    if (e.isIntersecting) {
      e.target.classList.add("visible");
      io.unobserve(e.target);
    }
  });
}, { threshold: 0.12 });
revealEls.forEach((el) => {
  el.classList.add("reveal");
  io.observe(el);
});

/* Contadores */
function animateCount(el) {
  const target = parseInt(el.dataset.count, 10);
  const suffix = el.dataset.suffix || "";
  const dur = 1200;
  const t0 = performance.now();
  function tick(now) {
    const p = Math.min((now - t0) / dur, 1);
    el.textContent = Math.floor(target * (1 - Math.pow(1 - p, 3))) + suffix;
    if (p < 1) requestAnimationFrame(tick);
    else el.textContent = target + suffix;
  }
  requestAnimationFrame(tick);
}
const statsIo = new IntersectionObserver((entries) => {
  entries.forEach((e) => {
    if (e.isIntersecting) {
      e.target.querySelectorAll(".stat__num").forEach(animateCount);
      statsIo.unobserve(e.target);
    }
  });
}, { threshold: 0.4 });
const statsEl = document.getElementById("stats");
if (statsEl) statsIo.observe(statsEl);

/* Terminal anima quando visível */
if ("IntersectionObserver" in window) {
  const term = document.querySelector(".term");
  const termIo = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        runSequence();
        termIo.disconnect();
      }
    });
  }, { threshold: 0.4 });
  termIo.observe(term);
} else {
  runSequence();
}
