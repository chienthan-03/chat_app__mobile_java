package com.example.chatapp.activities;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.example.chatapp.adapters.RecentConversationAdapter;
import com.example.chatapp.databinding.ActivityMainBinding;
import com.example.chatapp.listeners.ConversionListener;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends BaseActivity implements ConversionListener {

    private ActivityMainBinding binding;    // Chứa các  tham chiếu đến thành phần giao diện
    private PreferenceManager preferenceManager;    // Quản lý thông tin của người dùng
    private List<ChatMessage> conversations;    // Danh sách đại diện cho các cuộc trò chuyện gần đây
    private RecentConversationAdapter conversationAdapter;  // Custom adapter được sử dụng để hiển thị danh sách các cuộc trò chuyện gần đây lên giao diện
    private FirebaseFirestore database;     // Tham chiếu đến CSDL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager((getApplicationContext()));

        loadUserDetails();
        init();
        getToken();
        setListeners();
        listenConversations();
    }

    private void init() {
        conversations = new ArrayList<>();
        conversationAdapter = new RecentConversationAdapter(conversations, this);
        binding.conversationsRecyclerView.setAdapter(conversationAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.imageSignOut.setOnClickListener(v -> signOutWithConfirmation());
        binding.fabNewChat.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), UserActivity.class)));
        binding.textName.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), InfoActivity.class)));
        binding.imageProfile.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), InfoActivity.class)));
    }

    // Tải thông tin user và hiển thị lên giao diện
    private void loadUserDetails() {
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));      // Lấy tên người dùng
        byte[] bytes = android.util.Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);    // Lấy chuỗi ảnh đã được mã hóa
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);      // Ảnh của user từ dữ liệu byte
        binding.imageProfile.setImageBitmap(bitmap);    // Lấy ảnh ngưởi dùng
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Lắng nghe cuộc trò chuyện và cập nhật khi có sự thay đổi
    private void listenConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    // Lắng nghe sự thay đổi của cuộc trò chuyện
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {    // Kiểm tra dữ liệu trả về có thay đổi hay không
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {    // Thêm mới cuộc trò chuyện
                    // Lấy thông tin người gửi và người nhận
                    String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    ChatMessage chatMessage = new ChatMessage();    // Đại diện cho cuộc trò chuyện
                    chatMessage.senderId = senderId;
                    chatMessage.receiverId = receiverId;
                    if (preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId)) {      // Kiểm tra người gửi có phải là người dùng hiện tại không
                        // Nếu người gửi là người dùng hiện tại thì sử dụng thông tin người nhận
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    } else {
                        // Nếu không phải thì thông tin người gửi sẽ được sử sụng
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    }
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);   // Lấy thông tin tin nhắn cuối cùng
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);     // Lấy thời gian
                    conversations.add(chatMessage);
                } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {  // Nếu cuộc trò chuyện đã tồn tại
                    for (int i = 0; i < conversations.size(); i++) {
                        // Lấy thông tin người nhận và người gửi
                        String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        if (conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)) {
                            // Nếu tìm thấy cuộc trò chuyện tương ứng
                            conversations.get(i).message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);      // Lấy tin nhắn cuối cùng
                            conversations.get(i).dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);    // Lấy thời gian tin nhắn cuối cùng
                            break;
                        }
                    }
                }
            }
            // Sắp xếp cuộc trò chuyện theo thời gian giảm dần
            Collections.sort(conversations, (obj1, obj2) -> obj2.dateObject.compareTo((obj1.dateObject)));
            conversationAdapter.notifyDataSetChanged();     // Thông báo cho adapter là dữ liệu cuộc trò chuyện đã được thay đổi
            binding.conversationsRecyclerView.smoothScrollToPosition(0);
            binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };

    // Token FCM giúp ứng dụng tương tác với người dùng
    // Lấy mã token FCM của thiết bị
    private void getToken() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this::updateToken);
    }

    // Cập nhật mã token FCM
    private void updateToken(String token) {
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token);    // Cập nhật mã thông báo FCM của người dùng
        FirebaseFirestore database = FirebaseFirestore.getInstance();   //Tương tác với CSDL
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(preferenceManager.getString(Constants.KEY_USER_ID));
        // Cập nhật mã thông báo FCM của người dùng trong tài liệu Firestore tương ứng
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast("Unable to update token"));
    }

    // Đăng xuất
    private void signOutWithConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Logout")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    signOut();
                })
                .setNegativeButton("No", (dialog, which) -> {

                })
                .show();
    }

    private void signOut() {
        showToast("Signing out ...");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(preferenceManager.getString(Constants.KEY_USER_ID));
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());      // Thêm một cập nhật vào HashMap để xóa giá trị của mã FCM
        // Cập nhật trên tài liệu sử dụng các cập nhật đã được cung cấp
        documentReference.update(updates).addOnSuccessListener(unused -> {
            preferenceManager.clear();
            startActivity(new Intent(getApplicationContext(), SignInActivity.class));
            finish();
        })
                .addOnFailureListener(e -> showToast("Unable to sign out"));
    }

    // Sự kiện click và chuyển dữ liệu user sang ChatActivity
    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);      // Chèn dữ liệu user vào ChatActivity
        startActivity(intent);
    }
}