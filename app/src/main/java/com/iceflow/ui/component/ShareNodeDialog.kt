package com.iceflow.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.model.ServerProfile

@Composable
fun ShareNodeDialog(
    node: ServerProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareLink = remember { ShareLinkParser.encode(node) }
    var showQrCode by remember { mutableStateOf(true) }
    val qrBitmap = remember(shareLink) {
        generateQRCode(shareLink, 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享节点 - ${node.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showQrCode && qrBitmap != null) {
                    // 显示二维码
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "节点二维码",
                            modifier = Modifier.size(240.dp)
                        )
                    }
                    Text(
                        "扫描二维码导入节点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 显示分享链接
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = shareLink,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showQrCode = true }) { Text("二维码") }
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("节点分享链接", shareLink))
                    showQrCode = false
                }) {
                    Text("复制链接")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun generateQRCode(text: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
