package org.example.pdfwrangler

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<PdfWranglerApplication>().with(TestcontainersConfiguration::class).run(*args)
}
