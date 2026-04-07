package com.gearsales.leadengine.config

import io.github.cdimascio.dotenv.Dotenv
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Lê variáveis do processo ([System.getenv]) e, em seguida, do arquivo `.env`.
 * **Prioridade:** o que já estiver exportado no ambiente do sistema / IDE vence o arquivo.
 *
 * A JVM **não** carrega `.env` sozinha; sem este helper, apenas `export VAR=...` funcionaria.
 */
object AppEnv {
    private val log = LoggerFactory.getLogger(AppEnv::class.java)

    private val dotenv: Dotenv? = run {
        val envFile = findEnvFile()
        if (envFile == null || !envFile.isFile) {
            return@run null
        }
        runCatching {
            Dotenv.configure()
                .directory(envFile.parentFile.absolutePath)
                .filename(envFile.name)
                .load()
        }.onSuccess {
            log.info(
                "Arquivo .env carregado de {} (variáveis já definidas no ambiente do processo têm prioridade).",
                envFile.absolutePath,
            )
        }.onFailure { e ->
            log.warn("Não foi possível ler .env em {}: {}", envFile.absolutePath, e.message)
        }.getOrNull()
    }

    private fun findEnvFile(): File? {
        var dir = File(System.getProperty("user.dir") ?: ".")
        repeat(8) {
            val f = File(dir, ".env")
            if (f.isFile) return f
            dir = dir.parentFile ?: return null
        }
        return null
    }

    /**
     * Valor efetivo: ambiente do processo primeiro, depois `.env`.
     */
    fun get(name: String): String? {
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return dotenv?.get(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
