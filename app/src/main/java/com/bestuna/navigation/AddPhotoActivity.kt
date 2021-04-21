package com.bestuna.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.bestuna.howlstagram.R
import com.bestuna.howlstagram.databinding.ActivityAddPhotoBinding
import com.bestuna.navigation.model.ContentDTO
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import java.text.SimpleDateFormat
import java.util.*

class AddPhotoActivity : AppCompatActivity() {

    lateinit var bb: ActivityAddPhotoBinding

    var PICK_IMAGE_FROM_ALBUM = 0
    var storage : FirebaseStorage? = null
    var photoUri: Uri? = null
    // user의 정보를 가져올 수 있도로 firebase auth클래스 추가
    var auth: FirebaseAuth? = null
    // database이용할수 있도록 파이어스토어 추가
    var firestore: FirebaseFirestore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bb = DataBindingUtil.setContentView(this, R.layout.activity_add_photo)

        // init storage
        storage = FirebaseStorage.getInstance()
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // open album
        var photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"
        startActivityForResult(photoPickerIntent, PICK_IMAGE_FROM_ALBUM)

        // add image upload event
        bb.addphotoBtnUpload.setOnClickListener {
            contentUpload()
        }

    }

    fun contentUpload() {
        var timestamp = SimpleDateFormat("yyyyMMdd_HHmmss")!!.format(Date())
        var imageFileName = "IMAGE_${timestamp}_.png"

        var storageRef = storage?.reference?.child("images")?.child(imageFileName)

        //FileUpload
        // 2가지 방식의 업로드 (promise방식, callback 방식)

        // promise method -> 구글에서 권장
        storageRef?.putFile(photoUri!!)?.continueWithTask { task: Task<UploadTask.TaskSnapshot> ->
            return@continueWithTask storageRef.downloadUrl
        }?.addOnSuccessListener { uri ->
            // 이미지 주소 받아오자 마자 데이터 모델 생성
            var contentDtO = ContentDTO()
            // insert info
            contentDtO.imageUrl = uri.toString()
            contentDtO.uid = auth?.currentUser?.uid
            contentDtO.userId = auth?.currentUser?.email
            contentDtO.explain = bb.addphotoEditExplain.text.toString()
            contentDtO.timestamp = System.currentTimeMillis()

            firestore?.collection("images")?.document()?.set(contentDtO)

            setResult(Activity.RESULT_OK)
            finish()
        }

        // Callback method
//        storageRef?.putFile(photoUri!!)?.addOnSuccessListener {
//            storageRef.downloadUrl.addOnSuccessListener { uri ->
//                // 이미지 주소 받아오자 마자 데이터 모델 생성
//                var contentDtO = ContentDTO()
//                // insert info
//                contentDtO.imageUrl = uri.toString()
//                contentDtO.uid = auth?.currentUser?.uid
//                contentDtO.userId = auth?.currentUser?.email
//                contentDtO.explain = bb.addphotoEditExplain.text.toString()
//                contentDtO.timestamp = System.currentTimeMillis()
//
//                firestore?.collection("images")?.document()?.set(contentDtO)
//
//                setResult(Activity.RESULT_OK)
//                finish()
//            }
//        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_FROM_ALBUM) {
            if (resultCode == Activity.RESULT_OK) {
                // 사진경로 넘어옴
                photoUri = data?.data
                bb.addPhotoImage.setImageURI(photoUri)
            } else {
                // 취소버튼 눌렀을 시 작동
                finish()
            }
        }
    }
}