package com.magneta.emeritsipmanager

import androidx.compose.runtime.MutableState

inline fun <T> MutableState<List<T>>.mutate(action: MutableList<T>.() -> Unit) {
    val mutableCopy = this.value.toMutableList()
    action(mutableCopy)
    this.value = mutableCopy
}