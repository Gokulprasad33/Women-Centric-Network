package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.R

class ChatScreen : Fragment(R.layout.fragment_chat) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val navController = findNavController()

        view.findViewById<ImageView>(R.id.navHome).setOnClickListener {
            navController.navigate(R.id.homeFragment)
        }

        view.findViewById<ImageView>(R.id.navChat).setOnClickListener {
            navController.navigate(R.id.chatFragment)
        }

        view.findViewById<ImageView>(R.id.navProfile).setOnClickListener {
            navController.navigate(R.id.profileFragment)
        }

        view.findViewById<ImageView>(R.id.navCommunity).setOnClickListener {
            navController.navigate(R.id.communityFragment)
        }

    }

}