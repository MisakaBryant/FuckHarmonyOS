package com.example.chatdiary.ui.view.main.diary


import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatdiary.data.Diary
import com.example.chatdiary.service.AMQPProducer
import com.example.chatdiary.service.DiaryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject


data class DiaryRequest(
    val title: String,
    val content: String,
    val timestamp: String,
    val position: String,
    val type: String,
)

@HiltViewModel
class DiaryViewModel @Inject constructor(private val diaryService: DiaryService) : ViewModel() {


    var getGenData = MutableLiveData<List<DayDiaryVo>?>()
    val genDiaryList = mutableStateOf(emptyList<DayDiaryVo>())

    init {
        getGenData = getGenData(Int.MAX_VALUE.toLong())
    }


    fun addDiary(
        type: String = "TXT", position: String, content: String
    ): MutableLiveData<Boolean> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val currentDate = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())
        val newDiary = DiaryRequest(
            title = currentDate,
            content = content,
            timestamp = dateFormat.format(Date()),
            position = position,
            type = type
        )
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch {
            kotlin.runCatching {
                insertDiary(newDiary)
            }.onSuccess {
                result.value = true
            }.onFailure {
                result.value = false
                Log.d("addDiary", it.message.toString())
            }
        }
        return result
    }
    suspend fun insertDiary(newDiary: DiaryRequest){
        val amqpProducer = AMQPProducer()
        amqpProducer.sendDiary(newDiary)
    }
    fun getDiariesFlow(): MutableLiveData<List<Diary>> {

        val resultLiveData = MutableLiveData<List<Diary>>()
        viewModelScope.launch {

            flow {
                emit(diaryService.getDiaries())
            }.collect {
                val sortedData = it.data?.sortedByDescending { data -> data.timestamp }
                resultLiveData.value = sortedData
            }
        }
        return resultLiveData;
    }

    fun searchDiariesByKeywordFlow(keyword: String): MutableLiveData<List<Diary>> {
        val resultLiveData = MutableLiveData<List<Diary>>()
        viewModelScope.launch {
            flow {
                emit(diaryService.getDiaries())
            }.collect {
                val filteredDiaries = it.data?.filter { diary ->
                    diary.content.contains(keyword, ignoreCase = true)
                }
                val sortedData = filteredDiaries?.sortedByDescending { data -> data.timestamp }
                resultLiveData.value = sortedData
            }
        }
        return resultLiveData
    }

    fun searchDiariesByKeywordFlowAndDate(
        keyword: String, targetDate: LocalDate
    ): MutableLiveData<List<Diary>> {
        val resultLiveData = MutableLiveData<List<Diary>>()
        viewModelScope.launch {
            flow {
                emit(diaryService.getDiaries())
            }.collect {
                val filteredDiaries = it.data?.filter { diary ->
                    diary.content.contains(keyword, ignoreCase = true) && run {
                        val formatter = DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd HH:mm:ss.SSS", Locale.US
                        )
                        val diaryDateTime = LocalDateTime.parse(diary.timestamp, formatter)
                        val diaryDate = diaryDateTime.toLocalDate()
                        diaryDate == targetDate
                    }
                }
                val sortedData = filteredDiaries?.sortedByDescending { data -> data.timestamp }
                resultLiveData.value = sortedData
            }
        }
        return resultLiveData
    }

    fun searchDiariesByDateFlow(targetDate: LocalDate): MutableLiveData<List<Diary>> {
        val resultLiveData = MutableLiveData<List<Diary>>()
        viewModelScope.launch {
            flow {
                emit(diaryService.getDiaries())
            }.collect {
                val filteredDiaries = it.data?.filter { diary ->
                    run {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                        val diaryDateTime = LocalDateTime.parse(diary.timestamp, formatter)
                        val diaryDate = diaryDateTime.toLocalDate()
                        diaryDate == targetDate
                    }


                }
                val sortedData = filteredDiaries?.sortedByDescending { data -> data.timestamp }
                resultLiveData.value = sortedData
            }
        }
        return resultLiveData
    }

    fun uploadImage(
        type: String = "IMAGE", position: String, content: String = "", uris: List<String>
    ): MutableLiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        viewModelScope.launch {
            kotlin.runCatching {
                val imageParts = uris.map { uri ->
                    val file = File(uri)
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("image", file.name, requestFile)
                }
                diaryService.uploadImage(
                    type = type, position = position, content = content, image = imageParts
                )
            }.onSuccess {
                Log.w("sendImage", it.toString())
                result.value = true
            }.onFailure {
                Log.w("sendImage", it.toString())
                result.value = false
            }
        }
        return result

    }

    private fun getGenData(number: Long): MutableLiveData<List<DayDiaryVo>?> {
        val result = MutableLiveData<List<DayDiaryVo>?>()
        viewModelScope.launch {
            kotlin.runCatching {
                diaryService.getDiaryGenList(number)
            }.onSuccess {
                Log.w("sendImage", it.toString())
                result.value = it.data?.reversed()
            }.onFailure {
                Log.w("sendImage", it.toString())
                result.value = null
            }
        }
        return result
    }

}