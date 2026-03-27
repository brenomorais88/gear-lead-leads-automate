package com.gearsales.leadengine.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.thymeleaf.Thymeleaf
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun Application.configureTemplating() {
    install(Thymeleaf) {
        setTemplateResolver(
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "utf-8"
                templateMode = TemplateMode.HTML
                isCacheable = false
            },
        )
    }
}
