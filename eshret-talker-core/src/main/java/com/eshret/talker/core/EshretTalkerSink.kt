package com.eshret.talker.core

// Этот файл описывает sink-контракт библиотеки eshret_talker.
// Через sink логгер может выводить записи не только в память, но и в Logcat, файл или другой приёмник.

fun interface EshretTalkerSink {
    // Это метод отправки одной записи во внешний приёмник.
    fun log(entry: EshretTalkerLogEntry)
}

