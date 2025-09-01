package org.example.pdfwrangler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PdfWranglerApplication

fun main(args: Array<String>) {
    runApplication<PdfWranglerApplication>(*args)
}
