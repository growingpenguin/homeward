package app.homeward

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<HomewardApplication>().with(TestcontainersConfiguration::class).run(*args)
}
