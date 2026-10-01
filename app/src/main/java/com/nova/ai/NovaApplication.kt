package com.nova.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Nova — AI Chat & Agent Workspace.
 *
 * Marked with [HiltAndroidApp] to bootstrap the Dagger Hilt dependency graph
 * for the whole application.
 */
@HiltAndroidApp
class NovaApplication : Application()
