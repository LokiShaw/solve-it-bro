package com.solveitbro.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solveitbro.app.capture.CaptureScreen
import com.solveitbro.app.review.ReviewScreen

private object Routes {
    const val CAPTURE = "capture"
    const val REVIEW = "review?image={image}"

    fun review(image: Uri) = "review?image=${Uri.encode(image.toString())}"
}

@Composable
fun SolveItBroNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CAPTURE) {
        composable(Routes.CAPTURE) {
            CaptureScreen(onImageCaptured = { uri -> navController.navigate(Routes.review(uri)) })
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("image") { type = NavType.StringType }),
        ) { entry ->
            val image = Uri.parse(entry.arguments?.getString("image").orEmpty())
            ReviewScreen(image = image, onRetake = { navController.popBackStack() })
        }
    }
}
