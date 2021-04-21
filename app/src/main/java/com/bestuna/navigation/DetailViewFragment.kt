package com.bestuna.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bestuna.howlstagram.R
import com.bestuna.howlstagram.databinding.FragmentDetailBinding
import com.bestuna.howlstagram.databinding.ItemDetailBinding
import com.bestuna.navigation.model.ContentDTO
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DetailViewFragment: Fragment() {

    lateinit var bb: FragmentDetailBinding

    var firestore: FirebaseFirestore? = null

    var uid: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        bb = DataBindingUtil.inflate(inflater, R.layout.fragment_detail, container, false)

        firestore = FirebaseFirestore.getInstance()

        uid = FirebaseAuth.getInstance().currentUser?.uid

        bb.detailviewFrRecycler.adapter = DetailViewRecyclerViewAdapter()
        bb.detailviewFrRecycler.layoutManager = LinearLayoutManager(activity)
        return bb.root
    }

    inner class DetailViewRecyclerViewAdapter : RecyclerView.Adapter<DetailViewRecyclerViewAdapter.CustomViewholder>() {
        var contentDTOs: ArrayList<ContentDTO> = arrayListOf()
        var contentUidList: ArrayList<String> = arrayListOf()

        init {
            firestore?.collection("images")?.orderBy("timestamp")?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                contentDTOs.clear()
                contentUidList.clear()

                if (querySnapshot == null) return@addSnapshotListener

                for (snapshot in querySnapshot!!.documents) {
                    var item = snapshot.toObject(ContentDTO::class.java)
                    contentDTOs.add(item!!)
                    contentUidList.add(snapshot.id)
                }
                notifyDataSetChanged()
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewholder {
            var view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false)
            return CustomViewholder(ItemDetailBinding.bind(view))
        }

        inner class CustomViewholder(val binding: ItemDetailBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onBindViewHolder(holder: CustomViewholder, position: Int) {
            holder.binding.detailviewProfileTv.text = contentDTOs[position].userId
            Glide.with(holder.itemView.context).load(contentDTOs[position].imageUrl).into(holder.binding.detailviewItemImageviewContent)
            holder.binding.detailviewItemTextExplainTv.text = contentDTOs[position].explain
            Glide.with(holder.itemView.context).load(contentDTOs[position].imageUrl).into(holder.binding.detailviewProfileImage)
            holder.binding.detailviewItemFavoriteCounterTv.text = "Likes ${contentDTOs[position].favoriteCount}"
            holder.binding.detailviewItemFavoriteImageview.setOnClickListener { favoriteEvent(position) }

            // ㅍ페이지 로드 된 후
            if (contentDTOs[position].favorites.containsKey(uid)) {
                holder.binding.detailviewItemFavoriteImageview.setImageResource(R.drawable.ic_favorite)
            } else {
                holder.binding.detailviewItemFavoriteImageview.setImageResource(R.drawable.ic_favorite_border)
            }
            holder.binding.detailviewProfileImage.setOnClickListener {
                var fr = UserFragment()
                var bundle = Bundle()
                bundle.putString("destinationUid", contentDTOs[position].uid)
                bundle.putString("userId", contentDTOs[position].userId)
                fr.arguments = bundle
                activity?.supportFragmentManager?.beginTransaction()?.replace(R.id.main_content, fr)?.commit()
            }
        }

        override fun getItemCount(): Int = contentDTOs.size

        fun favoriteEvent(position: Int) {
            var tsDoc = firestore?.collection("images")?.document(contentUidList[position])
            firestore?.runTransaction { transaction ->

                var contentDTO = transaction.get(tsDoc!!).toObject(ContentDTO::class.java)

                if (contentDTO!!.favorites.containsKey(uid)) {
                    contentDTO.favoriteCount = contentDTO.favoriteCount - 1
                    contentDTO.favorites.remove(uid)
                } else {
                    contentDTO.favoriteCount = contentDTO.favoriteCount + 1
                    contentDTO.favorites[uid!!] = true
                }

                transaction.set(tsDoc, contentDTO)
            }
        }
    }
}