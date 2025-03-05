package com.example.kotlinquizzer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun parseQuizText(quizText: String): List<Question> {
    val questions = mutableListOf<Question>()
    val questionPattern = Regex("""^\s*(?:#|\d+[.)])\s*(.+)$""")
    val optionPattern = Regex("""^\s*[-•o]\s*(?:[a-zA-Z]\)?\s*)?(.*)$""")

    var currentQuestionText: String? = null
    val currentOptions = mutableListOf<String>()

    for (line in quizText.lines()) {
        val trimmedLine = line.trim()
        if (trimmedLine.isEmpty()) continue

        val questionMatch = questionPattern.matchEntire(trimmedLine)
        if (questionMatch != null) {
            if (currentQuestionText != null) {
                val labeledOptions = currentOptions.mapIndexed { index, option ->
                    "${('a' + index)} $option"
                }
                questions.add(Question(currentQuestionText, labeledOptions))
                currentOptions.clear()
            }
            currentQuestionText = questionMatch.groupValues[1].trim()
        } else {
            val optionMatch = optionPattern.matchEntire(trimmedLine)
            if (optionMatch != null) {
                val optionText = optionMatch.groupValues[1].trim()
                if (optionText.isNotEmpty()) {
                    currentOptions.add(optionText)
                }
            }
        }
    }

    if (currentQuestionText != null) {
        val labeledOptions = currentOptions.mapIndexed { index, option ->
            "${('a' + index)} $option"
        }
        questions.add(Question(currentQuestionText, labeledOptions))
    }

    return questions
}

fun generateQuizText(quiz: Quiz): String {
    val sb = StringBuilder()
    quiz.questions.forEach { question ->
        sb.append("# ").append(question.text).append("\n")
        question.options.forEach { option ->
            sb.append("- ").append(option).append("\n")
        }
        sb.append("\n")
    }
    return sb.toString().trim()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QuizApp() }
    }
}

@Composable
fun QuizApp() {
    val context = LocalContext.current
    val dbHelper = remember { QuizDatabaseHelper(context) }
    var currentScreen by remember { mutableStateOf("home") } // "home", "input", "edit", "quiz"
    var quizzes by remember { mutableStateOf(listOf<Quiz>()) }
    var selectedQuiz by remember { mutableStateOf<Quiz?>(null) }
    var quizNameInput by remember { mutableStateOf("") }
    var editingQuiz by remember { mutableStateOf<Quiz?>(null) }
    val scope = rememberCoroutineScope()
    var quizTextInput by remember {
        mutableStateOf(
            """
        # ¿Qué palabra clave se usa para declarar una variable inmutable en Kotlin?
        - var
        - let
        - val
        - const

        1. ¿Cuál es la función principal que se ejecuta en un programa Kotlin?
        • init
        • start
        • main
        • run
        """.trimIndent()
        )
    }

    // Carga los quizzes desde la base de datos al iniciar la app
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = dbHelper.getAllQuizzes()
            // Actualiza el hilo principal
            quizzes = loaded
        }
    }

    when (currentScreen) {
        "home" -> HomeScreen(
            quizzes = quizzes,
            onNewQuiz = {
                quizNameInput = ""
                quizTextInput = ""
                editingQuiz = null
                currentScreen = "input"
            },
            onQuizSelected = { quiz ->
                selectedQuiz = quiz
                currentScreen = "quiz"
            },
            onShareQuiz = { quiz -> shareQuiz(context, quiz) },
            onDownloadQuiz = { quiz -> downloadQuizAsTxt(context, quiz) },
            onEditQuiz = { quiz ->
                editingQuiz = quiz
                quizNameInput = quiz.name
                quizTextInput = generateQuizText(quiz)
                currentScreen = "edit"
            },
            onDeleteQuiz = { quiz ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        dbHelper.deleteQuiz(quiz.id)
                    }
                    quizzes = quizzes.filter { it.id != quiz.id }
                }
            }
        )

        "input" -> {
            // Pantalla para crear un nuevo quiz
            QuizInputScreen(
                quizNameInput = quizNameInput,
                onNameChanged = { quizNameInput = it },
                quizTextInput = quizTextInput,
                onTextChanged = { quizTextInput = it },
                onStartQuiz = {
                    val questions = parseQuizText(quizTextInput)
                    if (questions.isNotEmpty()) {
                        val newQuiz = Quiz(id = 0, name = quizNameInput, questions = questions)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val newId = dbHelper.insertQuiz(newQuiz)
                                val insertedQuiz = newQuiz.copy(id = newId.toInt())
                                quizzes = quizzes + insertedQuiz
                                selectedQuiz = insertedQuiz
                            }
                            currentScreen = "quiz"
                        }
                    }
                },
                onCancel = { currentScreen = "home" }
            )
        }

        "edit" -> {
            QuizInputScreen(
                quizNameInput = quizNameInput,
                onNameChanged = { quizNameInput = it },
                quizTextInput = quizTextInput,
                onTextChanged = { quizTextInput = it },
                onStartQuiz = {
                    val questions = parseQuizText(quizTextInput)
                    if (questions.isNotEmpty() && editingQuiz != null) {
                        val updatedQuiz =
                            editingQuiz!!.copy(name = quizNameInput, questions = questions)
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                dbHelper.updateQuiz(updatedQuiz)
                            }
                            quizzes =
                                quizzes.map { if (it.id == updatedQuiz.id) updatedQuiz else it }
                            selectedQuiz = updatedQuiz
                            currentScreen = "quiz"
                        }
                    }
                },
                onCancel = { currentScreen = "home" }
            )
        }

        "quiz" -> {
            selectedQuiz?.let { quiz ->
                QuizViewScreen(quiz = quiz, onFinish = { responses ->
                    val updateQuiz = quiz.copy(responses = responses)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dbHelper.updateQuiz(updateQuiz)
                        }
                        quizzes = quizzes.map { if (it.id == quiz.id) updateQuiz else it }
                        currentScreen = "home"
                    }
                })
            }
        }
    }
}

@Composable
fun HomeScreen(
    quizzes: List<Quiz>,
    onNewQuiz: () -> Unit,
    onQuizSelected: (Quiz) -> Unit,
    onShareQuiz: (Quiz) -> Unit,
    onDownloadQuiz: (Quiz) -> Unit,
    onEditQuiz: (Quiz) -> Unit,
    onDeleteQuiz: (Quiz) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNewQuiz) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Quiz")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Quizzes Generados",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            if (quizzes.isEmpty()) {
                Text("No hay quizzes generados", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(quizzes) { quiz ->
                        QuizListItem(
                            quiz = quiz,
                            onQuizSelected = { onQuizSelected(quiz) },
                            onShareQuiz = { onShareQuiz(quiz) },
                            onDownloadQuiz = { onDownloadQuiz(quiz) },
                            onEditQuiz = { onEditQuiz(quiz) },
                            onDeleteQuiz = { onDeleteQuiz(quiz) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuizListItem(
    quiz: Quiz,
    onQuizSelected: () -> Unit,
    onShareQuiz: () -> Unit,
    onDownloadQuiz: () -> Unit,
    onEditQuiz: () -> Unit,
    onDeleteQuiz: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onQuizSelected() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = quiz.name)
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        onClick = {
                            expanded = false
                            onShareQuiz()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Descargar .txt") },
                        onClick = {
                            expanded = false
                            onDownloadQuiz()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = {
                            expanded = false
                            onEditQuiz()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            expanded = false
                            onDeleteQuiz()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizInputScreen(
    quizNameInput: String,
    onNameChanged: (String) -> Unit,
    quizTextInput: String,
    onTextChanged: (String) -> Unit,
    onStartQuiz: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nuevo Quiz") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = "Nombre del Quiz: ", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = quizNameInput,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ingresa el nombre del quiz") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Ingrese el texto del quiz:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = quizTextInput,
                onValueChange = onTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("Pega aquí el texto del quiz...") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = onStartQuiz,
                    enabled = quizTextInput.isNotBlank() && quizTextInput.isNotBlank()
                ) {
                    Text("Generar Quiz")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizViewScreen(quiz: Quiz, onFinish: (List<String>) -> Unit) {
    var currentQuestionIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<String?>(null) }
    val responses = remember { mutableStateListOf<String>() }

    Scaffold(topBar = { TopAppBar(title = { Text("Quiz: ${quiz.name}") }) }) { padding ->
        if (currentQuestionIndex < quiz.questions.size) {
            val currentQuestion = quiz.questions[currentQuestionIndex]
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(text = currentQuestion.text, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                currentQuestion.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = option }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { selectedOption = option }
                        )
                        Text(text = option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (selectedOption != null) {
                            responses.add(selectedOption!!)
                            currentQuestionIndex++
                            selectedOption = null
                        }
                    },
                    enabled = selectedOption != null
                ) {
                    Text("Siguiente")
                }
            }
        } else {
            onFinish(responses)
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("¡Has terminado el quiz!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

fun shareQuiz(context: Context, quiz: Quiz) {
    val shareText = buildString {
        quiz.questions.forEachIndexed { index, question ->
            append("${index + 1}. ${question.text}\n")
            if (quiz.responses.size > index) {
                append("Respuesta: ${quiz.responses[index]}\n\n")
            } else {
                append("Respuesta: \n\n")
            }
        }
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir Quiz"))
}

fun downloadQuizAsTxt(context: Context, quiz: Quiz) {
    val fileContent = buildString {
        quiz.questions.forEachIndexed { index, question ->
            append("${index + 1}. ${question.text}\n")
            if (quiz.responses.size > index) {
                append("Respuesta: ${quiz.responses[index]}\n\n")
            } else {
                append("Respuesta: \n\n")
            }
        }
    }
    val fileName = "${quiz.name}.txt"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(fileContent.toByteArray())
        }
        Toast.makeText(context, "Archivo guardado en Downloads", Toast.LENGTH_LONG).show()
    } else {
        Toast.makeText(context, "Error al guardar el archivo", Toast.LENGTH_LONG).show()
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewDefault() {
    QuizApp()
}

