package com.example.myapplication.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import com.example.myapplication.base.BaseActivity
import com.example.myapplication.data.enumm.FaceDetectionResult
import com.example.myapplication.databinding.ActivityHomeBinding
import com.example.myapplication.ui.dialog.DialogCheckFaceId
import com.example.myapplication.ui.dialog.DialogTypeChoosePhoto
import com.example.myapplication.ui.dialog.UploadErrorDialog
import com.example.myapplication.ui.generate.GenerateActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream

class HomeActivity : BaseActivity<ActivityHomeBinding>(
    inflater = ActivityHomeBinding::inflate
) {
    // =================================================================
// 1. Camera + Gallery launcher (KHÔNG còn xin quyền camera)
// =================================================================
   private var tempCameraFile: File?=null

   private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()){uri ->
       uri?.let{ handleImageUri(it) }
   }

   private val pickPhotoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()){uri ->
       uri?.let{ handleImageUri(it) }
   }

   private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()){success ->
       if(success){
           tempCameraFile?.let { checkFaceAndProceed(it) }
       }
   }

// ❌ Removed: RequestPermission launcher (không xin lại ở đây)


// =================================================================
// 2. BottomSheet chọn ảnh
// =================================================================

  private var photoBottomSheet: DialogTypeChoosePhoto?=null
  fun showPhotoPickerBottomSheet(){
      photoBottomSheet?.dismissAllowingStateLoss()
      photoBottomSheet = null

      photoBottomSheet = DialogTypeChoosePhoto(object : DialogTypeChoosePhoto.OnSelectedListener{
          override fun onPhotoSelected() {
              photoBottomSheet = null
              openPhotoPicker()
          }

          override fun onCameraSelected() {
              photoBottomSheet = null
              openCamera()
          }
      })
      if (!isFinishing && !isDestroyed && supportFragmentManager.isStateSaved.not()) {
          photoBottomSheet?.show(supportFragmentManager, "PhotoPickerBottomSheet")
      }

  }
// =================================================================
// 3. Gallery chọn ảnh
// =================================================================

    private fun openPhotoPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pickPhotoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            pickImageLauncher.launch("image/*")
        }
    }

// =================================================================
// 4. Camera (KHÔNG xin permission ở đây)
// =================================================================

    private fun openCamera() {
        if (!isCameraGranted()) {
            Toast.makeText(
                this,
                R.string.permission_camera_message,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        takePhotoWithCamera()
    }

    private fun isCameraGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun takePhotoWithCamera() {
        tempCameraFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            tempCameraFile!!
        )

        cameraLauncher.launch(uri)
    }


// =================================================================
// 5. Xử lý ảnh
// =================================================================

    fun Uri.copyToCacheFile(context: Context, filename: String): File? {
        return try {
            val file = File(context.cacheDir, filename)
            context.contentResolver.openInputStream(this)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleImageUri(uri: Uri) {
        val file = uri.copyToCacheFile(this, "gallery_${System.currentTimeMillis()}.jpg")
        file?.let { checkFaceAndProceed(it) }
    }


// =================================================================
// 6. ML Kit detection
// =================================================================

    private fun checkFaceAndProceed(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
            return
        }

        detectFaceInImage(file) { result ->
            runOnUiThread {
                when (result) {
                    FaceDetectionResult.SingleGoodFace -> {
                        openGenerateActivity(file)
                    }
                    FaceDetectionResult.NoFace -> {
                        showFaceErrorDialog(
                            title = getString(R.string.no_face_detected),
                            message = getString(R.string.please_try_again_with_a_frontal_portrait_photo),
                            tag = "NoFaceDialog"
                        )
                    }

                    FaceDetectionResult.MultipleFaces -> {
                        showFaceErrorDialog(
                            title = getString(R.string.more_than_one_face_detected),
                            message = getString(R.string.please_try_again_with_a_single_faced_frontal_portrait),
                            tag = "MultiFaceDialog"
                        )
                    }

                    FaceDetectionResult.Error -> {
                        UploadErrorDialog(
                            context = this,
                            action = { showPhotoPickerBottomSheet() },
                            dismissAction = {}
                        ).show()
                    }
                }
            }
        }
    }

    private fun showFaceErrorDialog(title: String, message: String, tag: String) {
        DialogCheckFaceId(
            title = title,
            content = message,
            action = { showPhotoPickerBottomSheet() }
        ).show(supportFragmentManager, tag)
    }


// =================================================================
// 7. ML Kit Implementation
// =================================================================

    private fun detectFaceInImage(file: File, callback: (FaceDetectionResult) -> Unit) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(file))
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()

        FaceDetection.getClient(options).process(image)
            .addOnSuccessListener { faces ->
                when {
                    faces.isEmpty() -> callback(FaceDetectionResult.NoFace)
                    faces.size > 1 -> callback(FaceDetectionResult.MultipleFaces)
                    else -> {
                        val face = faces[0]
                        val isFrontal =
                            kotlin.math.abs(face.headEulerAngleY) < 18f &&
                                    kotlin.math.abs(face.headEulerAngleZ) < 18f
                        val isEyesOpen =
                            (face.leftEyeOpenProbability ?: 0f) > 0.5f &&
                                    (face.rightEyeOpenProbability ?: 0f) > 0.5f
                        val isNotSmilingTooMuch =
                            (face.smilingProbability ?: 0f) < 0.8f

                        if (isFrontal && isEyesOpen && isNotSmilingTooMuch) {
                            callback(FaceDetectionResult.SingleGoodFace)
                        } else {
                            callback(FaceDetectionResult.Error)
                        }
                    }
                }
            }
            .addOnFailureListener { callback(FaceDetectionResult.Error) }
    }


// =================================================================
// 8. Extension tiện lợi
// =================================================================

    private fun Uri.toBitmap(context: Context): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, this)
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, this)
        }
    } catch (e: Exception) {
        null
    }

    private fun Bitmap.saveToCacheFile(context: Context, filename: String): File? {
        return try {
            val file = File(context.cacheDir, filename)
            FileOutputStream(file).use { out ->
                compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }


// =================================================================
// 9. onDestroy
// =================================================================

    override fun onDestroy() {
        photoBottomSheet?.dismissAllowingStateLoss()
        photoBottomSheet = null
        super.onDestroy()
    }

    private fun openGenerateActivity(file: File) {
        val intent = Intent(this, GenerateActivity::class.java).apply {
//            putExtra(Const.IMAGE_GALLERY, file.path)
//            putExtra(Const.IMAGE_TEMPLETE, selectedTemplatePath)
//            putExtra(Const.IMAGE_TEMPLETE_CATEGORY, selectedTitle)
//            putExtra(Const.ITEM_CODE, selectedItemCode)
//            putExtra(Const.IMAGE_TEMPLETE_TYPE, selectedType)
        }
        startActivity(intent)
    }


}



//registerForActivityResult : đăng ký một trình khởi chạy(launch) để nhận kết quả từ một hoạt động .
//ActivityResultContracts : hợp động để dùng các dịch cụ của registerForActivityResult
//GetContent() → mở hệ thống file picker (gallery) của thiết bị. android 12 về trước,/trả vee uri tham chieeus đến aảnh
//PickVisualMedia() mở hệ thống file picker (gallery) của thiết bị. android 13+,trả vee uri tham chieeus đến aảnh
//TakePicture() mở camera để chụp ảnh và trả về true  -> ảnh chụp được lưu thành công vào file bạn cung cấp,
//false -> thất bại
//RequestPermission() trả về boolean , hiển thị popup xin quyền,
//isGranted = true → user bấm Allow
//isGranted = false → user bấm Deny hoặc không cho phép
//.dismissAllowingStateLoss()
//→ Tắt/dismiss bottom sheet, cho phép mất state (tránh crash khi activity đang background).
