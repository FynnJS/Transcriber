package dev.fynnjs.transcriber.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 12.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(32.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            content()
        }
    }
}
