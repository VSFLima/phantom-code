package com.phantomcode.app.data.secrets

/**
 * Preset de integração (D8 — Toolbox → Integrações & API Keys).
 *
 * Cada serviço conhecido traz os defaults: nome, categoria, variável de
 * ambiente e dica de valor. O usuário escolhe o serviço e o app preenche
 * o formulário — é só colar a chave. Nada de digitar nome/envVar à mão.
 */
data class IntegrationPreset(
    val id: String,
    val name: String,
    val category: SecretCategory,
    val envVar: String,
    val valueHint: String,
    val docUrl: String? = null,
)

/** Catálogo de serviços conhecidos (expandível — adicione aqui). */
object IntegrationCatalog {

    val ALL: List<IntegrationPreset> = listOf(
        // ── IA ──
        IntegrationPreset("openai", "OpenAI (GPT)", SecretCategory.IA, "OPENAI_API_KEY", "sk-…", "https://platform.openai.com/api-keys"),
        IntegrationPreset("anthropic", "Anthropic (Claude)", SecretCategory.IA, "ANTHROPIC_API_KEY", "sk-ant-…", "https://console.anthropic.com"),
        IntegrationPreset("gemini", "Google Gemini", SecretCategory.IA, "GEMINI_API_KEY", "AIza…", "https://aistudio.google.com/apikey"),
        IntegrationPreset("groq", "Groq (Llama)", SecretCategory.IA, "GROQ_API_KEY", "gsk_…", "https://console.groq.com/keys"),
        IntegrationPreset("mistral", "Mistral", SecretCategory.IA, "MISTRAL_API_KEY", "…", "https://console.mistral.ai"),
        IntegrationPreset("huggingface", "Hugging Face", SecretCategory.IA, "HF_TOKEN", "hf_…", "https://huggingface.co/settings/tokens"),
        IntegrationPreset("ollama", "Ollama (local)", SecretCategory.IA, "OLLAMA_HOST", "http://localhost:11434"),

        // ── Git ──
        IntegrationPreset("github", "GitHub (PAT)", SecretCategory.GIT, "GITHUB_TOKEN", "ghp_…", "https://github.com/settings/tokens"),
        IntegrationPreset("gitlab", "GitLab", SecretCategory.GIT, "GITLAB_TOKEN", "glpat-…", "https://gitlab.com/-/user_settings/personal_access_tokens"),
        IntegrationPreset("bitbucket", "Bitbucket", SecretCategory.GIT, "BITBUCKET_TOKEN", "…", "https://bitbucket.org/account/settings/app-passwords"),

        // ── Cloud / Serviços web ──
        IntegrationPreset("google", "Google Cloud", SecretCategory.CLOUD, "GOOGLE_API_KEY", "AIza…", "https://console.cloud.google.com/apis/credentials"),
        IntegrationPreset("aws", "AWS", SecretCategory.CLOUD, "AWS_ACCESS_KEY_ID", "AKIA…", "https://console.aws.amazon.com/iam"),
        IntegrationPreset("azure", "Azure", SecretCategory.CLOUD, "AZURE_API_KEY", "…", "https://portal.azure.com"),
        IntegrationPreset("supabase", "Supabase", SecretCategory.CLOUD, "SUPABASE_URL", "https://…", "https://supabase.com/dashboard"),
        IntegrationPreset("firebase", "Firebase", SecretCategory.CLOUD, "FIREBASE_API_KEY", "AIza…", "https://console.firebase.google.com"),
        IntegrationPreset("cloudflare", "Cloudflare", SecretCategory.CLOUD, "CLOUDFLARE_API_TOKEN", "…", "https://dash.cloudflare.com/profile/api-tokens"),
        IntegrationPreset("vercel", "Vercel", SecretCategory.CLOUD, "VERCEL_TOKEN", "…", "https://vercel.com/account/tokens"),
        IntegrationPreset("digitalocean", "DigitalOcean", SecretCategory.CLOUD, "DIGITALOCEAN_TOKEN", "dop_…", "https://cloud.digitalocean.com/account/api/tokens"),
        IntegrationPreset("dropbox", "Dropbox", SecretCategory.CLOUD, "DROPBOX_TOKEN", "sl.…", "https://www.dropbox.com/developers/apps"),
        IntegrationPreset("googledrive", "Google Drive", SecretCategory.CLOUD, "GDRIVE_TOKEN", "…", "https://console.cloud.google.com/apis/credentials"),

        // ── Servidor / Infra ──
        IntegrationPreset("ftp", "FTP", SecretCategory.SERVER, "FTP_HOST", "ftp://servidor:porta"),
        IntegrationPreset("sftp", "SFTP / SSH", SecretCategory.SERVER, "SFTP_HOST", "usuario@servidor"),
        IntegrationPreset("webdav", "WebDAV", SecretCategory.SERVER, "WEBDAV_URL", "https://servidor/dav"),
        IntegrationPreset("smtp", "SMTP (e-mail)", SecretCategory.SERVER, "SMTP_HOST", "smtp.gmail.com:587"),
        IntegrationPreset("postgres", "PostgreSQL", SecretCategory.SERVER, "PG_HOST", "host:5432"),
        IntegrationPreset("mysql", "MySQL", SecretCategory.SERVER, "MYSQL_HOST", "host:3306"),
        IntegrationPreset("redis", "Redis", SecretCategory.SERVER, "REDIS_URL", "redis://host:6379"),
        IntegrationPreset("mongo", "MongoDB", SecretCategory.SERVER, "MONGO_URL", "mongodb://host:27017"),
    )

}
