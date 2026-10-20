package app.homeward

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HomewardApplication

fun main(args: Array<String>) {
    runApplication<HomewardApplication>(*args)
}
