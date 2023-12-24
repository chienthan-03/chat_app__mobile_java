package com.example.chatapp.activities;

import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.SnapHelper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.example.chatapp.adapters.UsersAdapter;
import com.example.chatapp.databinding.ActivityUserBinding;
import com.example.chatapp.listeners.UserListeners;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserActivity extends BaseActivity implements UserListeners {
    private ActivityUserBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        getUsers();

        // Hiệu ứng cuộn dọc danh sách người dùng
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(binding.usersRecyclerView);
    }

    @Override
    public void onConversionClicked(User user) {

    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
    }

    // Lấy danh sách người dùng từ Firestore và hiển thị lên giao diện
    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();   // Tương tác với CSDL
        database.collection(Constants.KEY_COLLECTION_USERS) // Truy cập vào bảng user(Constants.KEY_COLLECTION_USERS)
                .get()  // Gửi truy vấn đến tất cả tài liệu trong bảng
                .addOnCompleteListener(task -> {
                    loading(false);
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);  // Lấy ID của người dùng
                    if (task.isSuccessful() && task.getResult() != null) {      // Kiểm tra trạng thái dữ liệu
                        List<User> users = new ArrayList<>();     // Tạo danh sách người dùng
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {      // Lập qua các tài liệu trong kết quả
                            if (currentUserId.equals((queryDocumentSnapshot.getId()))) {    // Kiểm tra và bỏ qua người dùng hiện tại
                                continue;
                            }
                            // Lấy thông tin người dùng từ Document
                            User user = new User();
                            user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                            user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                            user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                            user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            user.id = queryDocumentSnapshot.getId();
                            users.add(user);    // Thêm người dùng vào danh sách
                        }
                        if (users.size() > 0) {
                            // Hiển thị ds người dùng
                            UsersAdapter usersAdapter = new UsersAdapter(users, this);
                            binding.usersRecyclerView.setAdapter(usersAdapter);
                            binding.usersRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            showErrorMessage();
                        }
                    } else {
                        showErrorMessage();
                    }
                });
    }

    private void showErrorMessage() {
        binding.textErrorMessage.setText(String.format("%s", "No user available"));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    // Khi bấn vào người dùng cụ thể sẽ hiển thị ChatActivity
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }
}