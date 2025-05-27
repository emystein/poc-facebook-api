package ar.com.flow.facebook

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
class PocFacebookApiApplication

fun main(args: Array<String>) {
    runApplication<PocFacebookApiApplication>(*args)
}
