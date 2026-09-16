package com.zayedmd.anteroom

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform