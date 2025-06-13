package com.example.photoeditorjc

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.photoeditorjc.ui.theme.PhotoEditorJCTheme

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoEditorJCTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhotoEditingApp()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditingApp() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val historyStack = remember { mutableStateListOf<Bitmap>() }
    val redoStack = remember { mutableStateListOf<Bitmap>() }
    val context = LocalContext.current

    val selectImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            val bmp = loadBitmapFromUri(it, context)
            bitmap = bmp
            historyStack.clear()
            redoStack.clear()
            historyStack.add(bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, true))
        }
    }

    var selectedFilter by remember { mutableStateOf("None") }
    val filters = listOf("None", "Grayscale", "Sepia", "Invert", "Brightness")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "🖼️ Photo Editor",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(end = 16.dp)
            ) {
                // Undo Button (visible only if can undo)
                if (historyStack.size > 1) {
                    FloatingActionButton(
                        onClick = {
                            redoStack.add(historyStack.removeLast())
                            bitmap = historyStack.last().copy(historyStack.last().config ?: Bitmap.Config.ARGB_8888, true)
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Undo")
                    }
                }

                // Redo Button (visible only if can redo)
                if (redoStack.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            val redoBmp = redoStack.removeLast()
                            bitmap = redoBmp
                            historyStack.add(redoBmp.copy(redoBmp.config ?: Bitmap.Config.ARGB_8888, true))
                        },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Redo")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Button(
                onClick = { selectImageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select an Image")
            }

            Spacer(modifier = Modifier.height(24.dp))

            bitmap?.let { bmp ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    elevation = CardDefaults.cardElevation(10.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected Image",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val rotated = rotateBitmap(bmp)
                            bitmap = rotated
                            historyStack.add(rotated.copy(rotated.config ?: Bitmap.Config.ARGB_8888, true))
                            redoStack.clear()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate")
                    }

                    FilterDropdown(filters, selectedFilter) {
                        selectedFilter = it
                        val filtered = applyFilter(it, bmp)
                        bitmap = filtered
                        historyStack.add(filtered.copy(filtered.config ?: Bitmap.Config.ARGB_8888, true))
                        redoStack.clear()
                    }

                    Button(
                        onClick = {
                            saveBitmapToGallery(context, bmp)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("\uD83D\uDCBE")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                var selectedAdjustment by remember { mutableStateOf<String?>(null) }
                var adjustmentValue by remember { mutableStateOf(0f) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            selectedAdjustment = "Brightness"
                            adjustmentValue = 0f
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Brightness")
                    }

                    Button(
                        onClick = {
                            selectedAdjustment = "Contrast"
                            adjustmentValue = 0f
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Contrast")
                    }

                    Button(
                        onClick = {
                            selectedAdjustment = "Highlights"
                            adjustmentValue = 0f
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Highlights")
                    }
                }

                selectedAdjustment?.let { adjustment ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("$adjustment: ${adjustmentValue.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = adjustmentValue,
                        onValueChange = {
                            adjustmentValue = it
                            val adjusted = when (adjustment) {
                                "Brightness" -> applyBrightnessFilter(bmp, it)
                                "Contrast" -> applyContrastFilter(bmp, it)
                                "Highlights" -> applyHighlightsFilter(bmp, it)
                                else -> bmp
                            }
                            bitmap = adjusted
                        },
                        valueRange = -100f..100f,
                        steps = 20,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Button(
                        onClick = {
                            val adjusted = when (adjustment) {
                                "Brightness" -> applyBrightnessFilter(bmp, adjustmentValue)
                                "Contrast" -> applyContrastFilter(bmp, adjustmentValue)
                                "Highlights" -> applyHighlightsFilter(bmp, adjustmentValue)
                                else -> bmp
                            }
                            bitmap = adjusted
                            historyStack.add(adjusted.copy(adjusted.config ?: Bitmap.Config.ARGB_8888, true))
                            redoStack.clear()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply $adjustment")
                    }
                }


            }
        }
    }
}

@Composable
fun FilterDropdown(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Face, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Filter: $selected")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            filters.forEach { filter ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(filter)
                    },
                    text = { Text(filter) }
                )
            }
        }
    }
}

fun applyFilter(filter: String, bmp: Bitmap): Bitmap = when (filter) {
    "Grayscale" -> applyGrayscaleFilter(bmp)
    "Sepia" -> applySepiaFilter(bmp)
    "Invert" -> applyInvertFilter(bmp)
    "Brightness" -> applyBrightnessFilter(bmp, 40f)
    else -> bmp
}

fun loadBitmapFromUri(uri: Uri, context: Context): Bitmap {
    val input = context.contentResolver.openInputStream(uri)
    return BitmapFactory.decodeStream(input) ?: throw IllegalArgumentException("Cannot decode bitmap")
}

fun rotateBitmap(src: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

fun applyGrayscaleFilter(src: Bitmap): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val cm = ColorMatrix().apply { setSaturation(0f) }
    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}

fun applySepiaFilter(src: Bitmap): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val sepiaMatrix = ColorMatrix().apply {
        setSaturation(0f)
        set(
            floatArrayOf(
                1f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 20f,
                0f, 0f, 1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    paint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}

fun applyInvertFilter(src: Bitmap): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val colorMatrix = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}

fun applyBrightnessFilter(src: Bitmap, value: Float): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val colorMatrix = ColorMatrix().apply {
        set(
            floatArrayOf(
                1f, 0f, 0f, 0f, value,
                0f, 1f, 0f, 0f, value,
                0f, 0f, 1f, 0f, value,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "PhotoEditor_${System.currentTimeMillis()}.jpg"
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoEditor")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        val outputStream = resolver.openOutputStream(it)
        outputStream.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream!!)) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                return
            }
        }
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        Toast.makeText(context, "Image saved to Gallery 📸", Toast.LENGTH_SHORT).show()
    } ?: Toast.makeText(context, "Failed to create MediaStore entry", Toast.LENGTH_SHORT).show()
}

fun applyContrastFilter(src: Bitmap, contrast: Float): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val scale = (contrast + 100f) / 100f
    val translate = (-0.5f * scale + 0.5f) * 255f

    val cm = ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    ))

    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}

fun applyHighlightsFilter(src: Bitmap, value: Float): Bitmap {
    val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val highlightFactor = value / 100f
    val cm = ColorMatrix(floatArrayOf(
        1f + highlightFactor, 0f, 0f, 0f, 0f,
        0f, 1f + highlightFactor, 0f, 0f, 0f,
        0f, 0f, 1f + highlightFactor, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))

    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(cm)
    }
    canvas.drawBitmap(src, 0f, 0f, paint)
    return bmp
}
