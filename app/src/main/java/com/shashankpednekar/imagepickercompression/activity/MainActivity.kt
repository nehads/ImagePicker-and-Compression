package com.shashankpednekar.imagepickercompression.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.shashankpednekar.imagepickercompression.BuildConfig
import com.shashankpednekar.imagepickercompression.ParentActivity
import com.shashankpednekar.imagepickercompression.R
import com.shashankpednekar.imagepickercompression.utils.compressImageFile
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

private const val REQ_CAPTURE = 100
private const val RES_IMAGE = 100

class MainActivity : ParentActivity(R.layout.activity_main) {
    private var queryImageUrl: String = ""
    private val tag = javaClass.simpleName
    private var imgPath: String = ""
    private var imageUri: Uri? = null
    private val permissions = arrayOf(Manifest.permission.CAMERA)
    private var pickerConst = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        camera_btn_camera.setOnClickListener {
            pickerConst = 0
            if (isPermissionsAllowed(permissions, true, REQ_CAPTURE)) {
                chooseCamera()
            }
        }

        gallery_btn_camera.setOnClickListener {
            pickerConst = 1
            if (isPermissionsAllowed(permissions, true, REQ_CAPTURE)) {
                chooseGallery()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_CAPTURE -> {
                if (isAllPermissionsGranted(grantResults)) {
                    if (pickerConst == 1) {
                        chooseGallery()
                    } else {
                        chooseCamera()
                    }
                } else {
                    openSetting()
                    Toast.makeText(
                        this,
                        getString(R.string.storage_not_granted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RES_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    handleImageRequest(data)
                }
            }
        }
    }

    private fun chooseCamera() {
        startActivityForResult(getPickImageIntent(), RES_IMAGE)
    }

    private fun chooseGallery() {
        startActivityForResult(getPickGalleryIntent(), RES_IMAGE)
    }

    private fun getPickImageIntent(): Intent? {
        var chooserIntent: Intent? = null

        var intentList: MutableList<Intent> = ArrayList()

        val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, setImageUri())

        intentList = addIntentsToList(this, intentList, takePhotoIntent)

        if (intentList.size > 0) {
            chooserIntent = Intent.createChooser(
                intentList.removeAt(intentList.size - 1),
                ""
            )
        }

        return chooserIntent
    }

    private fun getPickGalleryIntent(): Intent? {
        var chooserIntent: Intent? = null

        var intentList: MutableList<Intent> = ArrayList()

        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        intentList = addIntentsToList(this, intentList, pickIntent)

        if (intentList.size > 0) {
            chooserIntent = Intent.createChooser(
                intentList.removeAt(intentList.size - 1),
                ""
            )
        }

        return chooserIntent
    }

    private fun setImageUri(): Uri {
        val folder = File("${getExternalFilesDir(Environment.DIRECTORY_DCIM)}")
        folder.mkdirs()

        val file = File(folder, "Image_Tmp.jpg")
        if (file.exists())
            file.delete()
        file.createNewFile()
        imageUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + getString(R.string.file_provider_name),
            file
        )
        imgPath = file.absolutePath
        return imageUri!!
    }


    private fun addIntentsToList(
        context: Context,
        list: MutableList<Intent>,
        intent: Intent
    ): MutableList<Intent> {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val resInfo = context.packageManager.queryIntentActivities(intent, 0)
            for (resolveInfo in resInfo) {
                val packageName = resolveInfo.activityInfo.packageName
                val targetedIntent = Intent(intent)
                targetedIntent.setPackage(packageName)
                list.add(targetedIntent)
            }

        } else
            list.add(intent)
        return list
    }

    private fun handleImageRequest(data: Intent?) {
        val exceptionHandler = CoroutineExceptionHandler { _, t ->
            t.printStackTrace()
            progressBar.visibility = View.GONE
            Toast.makeText(
                this,
                t.localizedMessage ?: getString(R.string.some_err),
                Toast.LENGTH_SHORT
            ).show()
        }

        GlobalScope.launch(Dispatchers.Main + exceptionHandler) {
            progressBar.visibility = View.VISIBLE

            if (data?.data != null) {     //Photo from gallery
                imageUri = data.data
                queryImageUrl = imageUri?.path!!
                queryImageUrl = compressImageFile(queryImageUrl, false, imageUri!!)
            } else {
                queryImageUrl = imgPath ?: ""
                compressImageFile(queryImageUrl, uri = imageUri!!)
            }
            imageUri = Uri.fromFile(File(queryImageUrl))

            if (queryImageUrl.isNotEmpty()) {

                Glide.with(this@MainActivity)
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .load(queryImageUrl)
                    .into(iv_img)
            }
            progressBar.visibility = View.GONE
        }

    }

     fun openSetting() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            showSettingRequestDialog()
        }
    }

    private fun showSettingRequestDialog() {

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle(getString(R.string.request_permission))
        dialogBuilder.setMessage(getString(R.string.storage_permission_access))
        dialogBuilder.setPositiveButton(getString(R.string.open_settings)) { _, _ ->

            openAppSystemSettings()
        }
        dialogBuilder.setNegativeButton(getString(R.string.deny)) { _, _ ->
            //require permission toast
        }
        dialogBuilder.show()
    }

    private fun openAppSystemSettings() {
        startActivity(Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", packageName, null)
        })
    }
}
