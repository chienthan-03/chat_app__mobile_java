package com.example.chatapp.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public abstract class BaseActivity extends AppCompatActivity {
    private DocumentReference documentReference;    // Cho phép thực hiện các hoạt động như đọc, ghi, cập nhật và xóa dữ liệu từ tài liệu Firestore

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());   // Quản lý các thiết lập và dữ liệu người dùng. PreferenceManager thường được sử dụng để lưu trữ và truy xuất ID người dùng
        FirebaseFirestore database = FirebaseFirestore.getInstance();   // Tương tác với CSDL Firestore
        documentReference = database.collection(Constants.KEY_COLLECTION_USERS)     // Tham chiếu đến tài liệu của người dùng trong bảng users
                .document(preferenceManager.getString(Constants.KEY_USER_ID));      // Sử dụng ID người dùng để để xác dịnh tài liệu cụ thể
    }

    // Cập nhật trạng thái khả dụng của người dùng được cập nhật khi họ không còn hoạt động trong ứng dụng
    @Override
    protected void onPause() {
        super.onPause();
        documentReference.update(Constants.KEY_AVAILABILITY, 0);        // KEY_AVAILABILITY = 0 thể hiện người dùng không khả dụng trong thời điểm này
    }

    @Override
    protected void onResume() {
        super.onResume();
        documentReference.update(Constants.KEY_AVAILABILITY, 1);        // KEY_AVAILABILITY = 1 thể hiện người dùng đang khả dụng trong thời điểm này
    }

    public abstract void onConversionClicked(User user);
}
