package com.bestuna.navigation

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bestuna.howlstagram.LoginActivity
import com.bestuna.howlstagram.MainActivity
import com.bestuna.howlstagram.R
import com.bestuna.howlstagram.databinding.FragmentUserBinding
import com.bestuna.navigation.model.ContentDTO
import com.bestuna.navigation.model.FollowDTO
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.facebook.login.Login
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserFragment: Fragment() {
    lateinit var bb: FragmentUserBinding

    var fragmentView: View? = null
    var firestore: FirebaseFirestore? = null
    var uid: String? = null
    var auth: FirebaseAuth? = null
    var currentUserUid: String? = null

    companion object {
        var PICK_PROFILE_FROM_ALBUM = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bb = DataBindingUtil.inflate(inflater, R.layout.fragment_user, container, false)
        uid = arguments?.getString("destinationUid")
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserUid = auth?.currentUser?.uid

        if (uid == currentUserUid) {
            // my
            bb.accountBtnFollowSignout.text = getString(R.string.signout)
            bb.accountBtnFollowSignout.setOnClickListener {
                activity?.finish()
                startActivity(Intent(activity, LoginActivity::class.java))
                auth?.signOut()
            }
        } else {
            // other
            bb.accountBtnFollowSignout.text = getString(R.string.follow)
            var mainActivity = (activity as MainActivity)
            mainActivity.bb.toolbarUsername.text = arguments?.getString("userId")
            mainActivity.bb.toolbarBtnBack.setOnClickListener {
                mainActivity.bb.bottomNavigation.selectedItemId = R.id.action_home
            }
            mainActivity.bb.toolbarTitleImage.isVisible = false
            mainActivity.bb.toolbarUsername.isVisible = true
            mainActivity.bb.toolbarBtnBack.isVisible = true

            bb.accountBtnFollowSignout.setOnClickListener {
                requestFollow()
            }
        }
        bb.accountRecycler.adapter = UserFragmentRecyclerViewAdapter()
        bb.accountRecycler.layoutManager = GridLayoutManager(activity!!, 3)

        bb.accountIvProfile.setOnClickListener {
            var photoPickerIntent = Intent(Intent.ACTION_PICK)
            photoPickerIntent.type = "image/*"
            activity?.startActivityForResult(photoPickerIntent, PICK_PROFILE_FROM_ALBUM)
        }

        getProfileImage()
        getFollowerAndFollowing()
        return bb.root
    }

    fun getFollowerAndFollowing() {
        firestore?.collection("users")?.document(uid!!)?.addSnapshotListener { documentSnapshot, error ->
            if (documentSnapshot == null) return@addSnapshotListener
            var followDTO = documentSnapshot.toObject(FollowDTO::class.java)
            if (followDTO?.followingCount != null) {
                bb.accountTvFollowingCount.text = followDTO?.followingCount?.toString()
            }
            if (followDTO?.followerCount != null) {
                bb.accountTvFollowCount.text = followDTO?.followerCount?.toString()
                if (followDTO?.followers?.containsKey(currentUserUid!!)) {
                    bb.accountBtnFollowSignout.text = getString(R.string.follow_cancel)
                    bb.accountBtnFollowSignout.background?.setColorFilter(ContextCompat.getColor(activity!!, R.color.colorLightGray), PorterDuff.Mode.MULTIPLY)
                } else {
                    if (uid != currentUserUid) {
                        bb.accountBtnFollowSignout.text = getString(R.string.follow)
                        bb.accountBtnFollowSignout.background?.colorFilter = null
                    }
                }
            }
        }
    }

    fun requestFollow() {
        // save data to my account
        var tsDocFollowing = firestore?.collection("users")?.document(currentUserUid!!)
        firestore?.runTransaction { transition ->
            var followDTO = transition.get(tsDocFollowing!!).toObject(FollowDTO::class.java)
            if (followDTO == null) {
                followDTO = FollowDTO()
                followDTO!!.followerCount = 1
                followDTO!!.followers[uid!!] = true

                transition.set(tsDocFollowing, followDTO)
                return@runTransaction
            }

            if (followDTO.followings.containsKey(uid)) {
                // follow 한 상태
                followDTO?.followingCount = followDTO?.followingCount - 1
                followDTO?.followers?.remove(uid)
            } else {
                // follow 안한상태
                followDTO?.followingCount = followDTO?.followingCount + 1
                followDTO?.followers[uid!!] = true
            }
            transition.set(tsDocFollowing, followDTO)
            return@runTransaction
        }
        // save data to third person
        var tsDocFollower = firestore?.collection("users")?.document(uid!!)
        firestore?.runTransaction { transition ->
            var followDTO = transition.get(tsDocFollower!!).toObject(FollowDTO::class.java)
            if (followDTO == null) {
                followDTO = FollowDTO()
                followDTO!!.followerCount = 1
                followDTO!!.followers[currentUserUid!!] = true

                transition.set(tsDocFollower, followDTO!!)
                return@runTransaction
            }

            if (followDTO!!.followers.containsKey(currentUserUid)) {
                // it cancel my follower when i follow a third person
                followDTO!!.followerCount = followDTO!!.followerCount - 1
                followDTO!!.followers.remove(currentUserUid!!)
            } else {
                // it add my follower when i dont follow a third person
                followDTO!!.followerCount = followDTO!!.followerCount + 1
                followDTO!!.followers[currentUserUid!!] = true
            }
            transition.set(tsDocFollower, followDTO!!)
            return@runTransaction
        }
    }

    fun getProfileImage() {
        firestore?.collection("profileImage")?.document(uid!!)?.addSnapshotListener { documentSnapshot, error ->
            if (documentSnapshot == null) return@addSnapshotListener
            if (documentSnapshot.data != null) {
                var url = documentSnapshot?.data!!["image"]
                Glide.with(activity!!).load(url).apply(RequestOptions().circleCrop()).into(bb.accountIvProfile)
            }
        }
    }

    inner class UserFragmentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var contentDTOs: ArrayList<ContentDTO> = arrayListOf()

        init {
            firestore?.collection("images")?.whereEqualTo("uid", uid)
                ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    if (querySnapshot == null) return@addSnapshotListener

                    // get date
                    for (snapshot in querySnapshot.documents) {
                        contentDTOs.add(snapshot.toObject(ContentDTO::class.java)!!)
                    }
                    bb.accountTvPostCount.text = contentDTOs.size.toString()
                    notifyDataSetChanged()
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            var width = resources.displayMetrics.widthPixels / 3
            var imageView = ImageView(parent.context)
            imageView.layoutParams = LinearLayoutCompat.LayoutParams(width, width)
            return CustomViewholder(imageView)
        }

        inner class CustomViewholder(var imageView: ImageView) :
            RecyclerView.ViewHolder(imageView) {}

        override fun getItemCount(): Int {
            return contentDTOs.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            var imageView = (holder as CustomViewholder).imageView
            Glide.with(holder.itemView.context).load(contentDTOs[position].imageUrl)
                .apply(RequestOptions().centerCrop()).into(imageView)

        }
    }
}
