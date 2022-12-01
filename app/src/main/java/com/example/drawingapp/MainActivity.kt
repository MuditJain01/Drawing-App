package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var currentPaint : ImageButton? = null
    var customProgressDialog : Dialog? = null

    //For opening Gallery
    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode == RESULT_OK && result.data != null){
                var canvasBack : ImageView = findViewById(R.id.backIv)

                canvasBack.setImageURI(result.data?.data)
            }
        }
    //For Request Permission
    private val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted){
                    Toast.makeText(this,"Permission Granted",Toast.LENGTH_LONG).show()
                    val pickImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickImageIntent)

                }else{
                    if(permissionName==Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this,"Oops you denied the permission",Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayout : LinearLayout = findViewById(R.id.linearlayout)
        currentPaint = linearLayout[0] as ImageButton

        val setBrushSize : ImageButton = findViewById(R.id.select_size_btn)
        setBrushSize.setOnClickListener {
            showDialog()
        }

        val galleryBtn : ImageButton = findViewById(R.id.select_image_btn)
        galleryBtn.setOnClickListener{
            requestStoragePermission()
        }

        val undoBtn : ImageButton = findViewById(R.id.undo_btn)
        undoBtn.setOnClickListener{
            drawingView?.onClickUndo()
        }

        val saveBtn : ImageButton = findViewById(R.id.save_btn)
        saveBtn.setOnClickListener{
            if(isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch{
                    val flDrawingView : FrameLayout = findViewById(R.id.frameLayout)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }

    //Dialog for selecting brush size
    private fun showDialog(){
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.brush_size_dialouge)
        dialog.setTitle("Select size:")

        val smallBtn = dialog.findViewById<ImageButton>(R.id.small_brush)
        val mediumBtn = dialog.findViewById<ImageButton>(R.id.medium_brush)
        val largeBtn = dialog.findViewById<ImageButton>(R.id.large_brush)

        smallBtn.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            dialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            dialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            dialog.dismiss()
        }
        dialog.show()
    }

    //Function for changing color of brush
    fun onClick(view : View){
        if(currentPaint!=view){
            val imageButton = view as ImageButton
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.large_brush_back)
            )
            currentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.small_brush_back)
            )
            drawingView?.changeColor(imageButton.tag.toString())
            currentPaint = view
        }
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    //Function for requesting permission
    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Kids Drawing App","Kids drawing app needs to access your external storage.")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    //Dialog when permission is not given.
    private fun showRationaleDialog(title :String,message:String){
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message)
            .setPositiveButton("Cancel"){dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }

    //For Saving our image to Storage
    private fun getBitmapFromView(view : View) : Bitmap{

        //Define a bitmap with the same size as the view
        //Create bitmap : Returns a mutable bitmap with specified width and height
        val returnBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        //Build a canvas to it
        val canvas = Canvas(returnBitmap)

        //Get the view's background
        val bgDrawable = view.background
        if(bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        //Draw the view on the canvas
        view.draw(canvas)
        return returnBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap!=null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() +
                        File.separator + "KidDrawingApp_" + System.currentTimeMillis()/1000 + ".png"
                    )

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,"File Saved Successfully :$result",Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(
                                this@MainActivity,"Something went wrong",Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                catch (e :Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)

        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog!=null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result :String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path ,uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }
}