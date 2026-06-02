package com.cmp.image

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform