package com.nutriscan.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutriscan.data.repository.ReportRepository
import com.nutriscan.data.repository.WeeklyReportData
import com.nutriscan.export.PdfReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _reportData = MutableStateFlow<WeeklyReportData?>(null)
    val reportData: StateFlow<WeeklyReportData?> = _reportData

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _pdfFile = MutableStateFlow<File?>(null)
    val pdfFile: StateFlow<File?> = _pdfFile

    init {
        loadReport()
    }

    private fun loadReport() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _reportData.value = reportRepository.gatherWeeklyReport()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isLoading.value = false
        }
    }

    fun generatePdf() {
        val data = _reportData.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val file = PdfReportGenerator.generate(appContext, data)
                _pdfFile.value = file
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isGenerating.value = false
        }
    }

    /** Reset so the share flow can be triggered again. */
    fun clearPdfFile() {
        _pdfFile.value = null
    }
}
