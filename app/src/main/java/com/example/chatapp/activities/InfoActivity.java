package com.example.chatapp.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;

import com.example.chatapp.databinding.ActivityInfoBinding;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

public class InfoActivity extends BaseActivity {
    ActivityInfoBinding binding;
    PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(getApplicationContext());

        loadUserDetails();
    }

    @Override
    public void onConversionClicked(User user) {

    }

    private void loadUserDetails() {
        if (getIntent().getStringExtra(Constants.KEY_RECEIVER_ID) == null) {
            binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));      // Lấy tên người dùng
            byte[] bytes = android.util.Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);    // Lấy chuỗi ảnh đã được mã hóa
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);      // Ảnh của user từ dữ liệu byte
            binding.imageProfile.setImageBitmap(bitmap);    // Lấy ảnh ngưởi dùng
        } else {
            String receiverUserId = getIntent().getStringExtra(Constants.KEY_RECEIVER_ID);
            FirebaseFirestore database = FirebaseFirestore.getInstance();
            database.collection(Constants.KEY_COLLECTION_USERS)
                    .document(receiverUserId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String userName = documentSnapshot.getString(Constants.KEY_NAME);
                            String imageBase64 = documentSnapshot.getString(Constants.KEY_IMAGE);
                            binding.textName.setText(userName);
                            if (imageBase64 != null) {
                                byte[] imageBytes = android.util.Base64.decode(imageBase64, Base64.DEFAULT);
                                Bitmap imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                binding.imageProfile.setImageBitmap(imageBitmap);
                            }
                        }
                    });
        }
    }
}
