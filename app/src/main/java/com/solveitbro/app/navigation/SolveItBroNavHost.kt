package com.solveitbro.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solveitbro.app.capture.CaptureScreen
import com.solveitbro.app.data.Language
import com.solveitbro.app.review.ReviewScreen
import com.solveitbro.app.solve.ResultScreen

private object Routes {
    const val CAPTURE = "capture"
    const val REVIEW = "review?image={image}"
    const val RESULT = "result?image={image}&lang={lang}"

    fun review(image: Uri) = "review?image=${Uri.encode(image.toString())}"

    fun result(image: Uri, language: Language) =
        "result?image=${Uri.encode(image.toString())}&lang=${language.code}"
}

@Composable
fun SolveItBroNavHost() {
    val navController = rememberNavController()
    val backToCapture = { navController.popBackStack(Routes.CAPTURE, inclusive = false) }
    NavHost(navController = navController, startDestination = Routes.CAPTURE) {
        composable(Routes.CAPTURE) {
            CaptureScreen(onImageCaptured = { uri -> navController.navigate(Routes.review(uri)) })
        }
        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("image") { type = NavType.StringType }),
        ) { entry ->
            val image = Uri.parse(entry.arguments?.getString("image").orEmpty())
            ReviewScreen(
                image = image,
                onRetake = { navController.popBackStack() },
                onSolve = { cropped, language -> navController.navigate(Routes.result(cropped, language)) },
            )
        }
        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument("image") { type = NavType.StringType },
                navArgument("lang") { type = NavType.StringType },
            ),
        ) { entry ->
            ResultScreen(
                image = Uri.parse(entry.arguments?.getString("image").orEmpty()),
                language = Language.fromCode(entry.arguments?.getString("lang")),
                onBack = { navController.popBackStack() },
                onNewQuestion = { backToCapture() },
            )
        }
    }
}
