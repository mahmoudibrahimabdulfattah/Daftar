package com.mk.daftar.feature.home

/**
 * Whether Home offers pull-to-refresh. Phones do; desktop does not, because trackpad scrolling would start pulls it
 * cannot finish, and the refresh action in the top bar is always there.
 */
internal expect val pullToRefreshAvailable: Boolean
