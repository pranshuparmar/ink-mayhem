package com.pranshuparmar.inkmayhem

/**
 * Index-based iteration for per-frame paths: `for (x in list)` allocates an Iterator, which ART
 * does not optimise away. The list must not change size during iteration.
 */
inline fun <T> List<T>.fastEach(action: (T) -> Unit) {
    for (i in indices) action(this[i])
}
