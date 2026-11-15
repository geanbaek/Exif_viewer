package com.example.model

enum class ExifCategory(val labelKo: String) {
    CAMERA("카메라 · 렌즈"),
    EXPOSURE("노출 · 광학"),
    DATE_TIME("촬영 일시"),
    LOCATION("위치 · GPS"),
    TECHNICAL("이미지 · 파일"),
    OTHER("기타 메타데이터")
}

data class ExifTagEntry(
    val tagKey: String,
    val displayName: String,
    val rawValue: String,
    val formattedValue: String,
    val category: ExifCategory
)

data class CameraSummary(
    val make: String?,
    val model: String?,
    val lensMake: String?,
    val lensModel: String?,
    val software: String?,
    val artistOrOwner: String?,
    val copyright: String?
) {
    val displayDeviceTitle: String?
        get() {
            val cleanMake = make?.trim()?.takeIf { it.isNotEmpty() }
            val cleanModel = model?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                cleanMake != null && cleanModel != null -> {
                    if (cleanModel.startsWith(cleanMake, ignoreCase = true)) {
                        cleanModel
                    } else {
                        "$cleanMake $cleanModel"
                    }
                }
                cleanModel != null -> cleanModel
                cleanMake != null -> cleanMake
                else -> null
            }
        }

    val displayLensTitle: String?
        get() {
            val cleanLensModel = lensModel?.trim()?.takeIf { it.isNotEmpty() }
            val cleanLensMake = lensMake?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                cleanLensModel != null && cleanLensMake != null -> {
                    if (cleanLensModel.startsWith(cleanLensMake, ignoreCase = true)) {
                        cleanLensModel
                    } else {
                        "$cleanLensMake $cleanLensModel"
                    }
                }
                cleanLensModel != null -> cleanLensModel
                cleanLensMake != null -> cleanLensMake
                else -> null
            }
        }
}

data class ExposureSummary(
    val apertureDisplay: String?,
    val shutterSpeedDisplay: String?,
    val isoDisplay: String?,
    val focalLengthDisplay: String?,
    val focalLength35mmDisplay: String?,
    val exposureBiasDisplay: String?,
    val exposureProgram: String?,
    val exposureMode: String?,
    val meteringMode: String?,
    val whiteBalance: String?,
    val flash: String?,
    val lightSource: String?,
    val sceneCaptureType: String?
)

data class LocationSummary(
    val latitude: Double,
    val longitude: Double,
    val decimalDisplay: String,
    val dmsDisplay: String,
    val altitudeDisplay: String?,
    val gpsDateTimeDisplay: String?,
    val directionDisplay: String?,
    val processingMethod: String?
)

data class TechnicalSummary(
    val orientationDisplay: String?,
    val colorSpaceDisplay: String?,
    val resolutionDpiDisplay: String?,
    val compressionDisplay: String?,
    val bitsPerSampleDisplay: String?,
    val sensingMethodDisplay: String?,
    val customRenderedDisplay: String?
)

data class ExifPhotoReport(
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long?,
    val fileSizeDisplay: String?,
    val mimeType: String?,
    val formatLabel: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val megapixelsDisplay: String?,
    val aspectRatioDisplay: String?,
    val capturedDateDisplay: String?,
    val digitizedDateDisplay: String?,
    val modifiedDateDisplay: String?,
    val cameraSummary: CameraSummary,
    val exposureSummary: ExposureSummary,
    val locationSummary: LocationSummary?,
    val technicalSummary: TechnicalSummary,
    val rawTags: List<ExifTagEntry>
) {
    val hasExifData: Boolean
        get() = rawTags.isNotEmpty()

    val dimensionsDisplay: String?
        get() = if (imageWidth > 0 && imageHeight > 0) {
            "$imageWidth × $imageHeight px"
        } else {
            null
        }
}

enum class InspectorTab(val labelKo: String) {
    OVERVIEW("요약 정보"),
    ALL_TAGS("전체 태그")
}

sealed interface ExifUiState {
    data object Empty : ExifUiState
    data object Loading : ExifUiState
    data class Success(val report: ExifPhotoReport) : ExifUiState
    data class Error(val message: String) : ExifUiState
}
