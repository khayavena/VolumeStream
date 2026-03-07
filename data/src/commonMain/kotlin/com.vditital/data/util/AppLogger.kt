package com.vditital.data.util

import io.github.aakira.napier.Napier

object AppLogger {

    fun init() = initLogging()

    fun d(tag: String, message: String) = Napier.d(message, tag = tag)

    fun i(tag: String, message: String) = Napier.i(message, tag = tag)

    fun w(tag: String, message: String) = Napier.w(message, tag = tag)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        Napier.e(message, throwable, tag = tag)
}

expect fun initLogging()
