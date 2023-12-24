package com.example.chatapp.activities;

import androidx.annotation.NonNull;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.example.chatapp.adapters.ChatAdapter;
import com.example.chatapp.databinding.ActivityChatBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;
import com.example.chatapp.network.ApiClient;
import com.example.chatapp.network.ApiService;
import com.example.chatapp.utilities.Constants;
import com.example.chatapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {
    private ActivityChatBinding binding;    // Dùng để truy cập đến giao diện của layout activity_chat.xml
    private User receiverUser;  // Chưa thông tin người dùng trong class User
    private List<ChatMessage> chatMessages; // Danh sách chứa các tin nhắn trong cuộc trò chuyện giữa người dùng hiện tại và người dùng đang nhắn tin
    private ChatAdapter chatAdapter;    // Hiển thị danh sách các tin nắn trong RecyclerView
    private PreferenceManager preferenceManager;    // Quản lý các thiết lập và dữ liệu người dùng
    private FirebaseFirestore database;     // Cho phép tương tác với CSDL Firestore
    private String conversionId = null;     // Biến đại diện cho ID của cuộc trò chuyện
    private Boolean isReceiverAvailable = false;    // Kiểm tra người dùng đang nhắn tin có online hay không

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferenceManager = new PreferenceManager(getApplicationContext());

        setListeners();
        loadReceiverDetails();
        init();
        listenMessage();
        setInfo();
    }

    // Phương thức để khởi tạo môi trường và các thành phần cần thiết cho việc hiển thị tin nhắn
    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());     // Dùng để quản lý các thiết lập và dữ liệu người dùng
        chatMessages = new ArrayList<>();   // Tạo danh sách trống để chứa tin nhắn cuộc trò chuyện
        chatAdapter = new ChatAdapter(      // Hiển thị danh sách tin nhắn trong RecyclerView
                chatMessages,   // Là danh sách chứa các tin nhắn trong cuộc trò chuyện
                getBitmapFromEncodedString(receiverUser.image),     // Hình ảnh người dùng đang nhắn tin, được chuyển đổi từ dữ liệu ảnh đã được mã hóa
                preferenceManager.getString(Constants.KEY_USER_ID)      // ID của người dùng diện tại. Được sử dụng để xác định người dùng gửi tin nhắn
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);   // Gán chatAdapter cho chatRecyclerView của giao diện. Adapter sẽ quản lý việc hiển thị các tin nhắn trong khung chat
        database = FirebaseFirestore.getInstance();     // Tương tác với CSDL
    }

    // Gửi tin nhắn từ người dùng hiện tại đến người dùng đang nhắn tin
    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();      // Chứa dữ liệu tin nhắn
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));   // Thêm ID người gửi tin nhắn đến vào HashMap
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);    // Thêm ID người dùng nhắn vào HasMap
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());      // Thêm nội dung của tin nhắn
        message.put(Constants.KEY_TIMESTAMP, new Date());       // Thêm thời gian của tin nhắn
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);    // Thêm HashMap vừa tạo vào bảng chat trong Firestore để lưu trữ tin nhắn
        if (conversionId != null) {     // Kiểm tra cuộc trò chuyện đã tồn tại hay chưa
            updateConversion(binding.inputMessage.getText().toString());    // Nếu đã tồn tại thì cập nhật nội dung cuộc trò chuyện
        } else {       // Nếu cuộc trò chuyện chưa tồn tại
            HashMap<String, Object> conversion = new HashMap<>();      // Tạo HashMap để lưu trữ thông tin cuộc trò chuyện
            // Thêm thông tin của người gửi và người nhận vào HashMap
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);      // Thêm cuộc trò chuyện vào CSDL
        }
        if (!isReceiverAvailable) {     // Kiểm tra người nhận tin nhắn có online hay không, nếu không thì gửi thông báo cho người dùng
            // Gửi thông báo vào đẩy đến cho người nhận khi học không online
            try {
                JSONArray tokens = new JSONArray();     // Xác định thiết bị mà thông báo được gửi đến
                tokens.put(receiverUser.token);     // Thêm token của người nhận vào JSONArray

                JSONObject data = new JSONObject();     // Chứa dữ liệu của thông báo
                // Thêm dữ liệu của người dùng vào JSONObject
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();     // Chứa dữ liệu gửi đến dịch vụ gửi thông báo (FCM)
                body.put(Constants.REMOTE_MSG_DATA, data);      // Thêm dữ liệu của thông báo
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);    // Thêm ds token của người nhận

                sendNotification(body.toString());      // Dùng để gửi thông báo với dữ liệu đã chuẩn bị
            } catch (Exception exception) {
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);     // Xóa nội dung tin nhắn trong inputMessage khi đã gủi tin nhắn
    }

    // Hiển thị thông báo
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Gửi thông báo bằng cách gọi API
    private void sendNotification(String messageBody) {
        // Gọi một API bằng cách sử dụng Retrofit
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(),    // Phương pháp tĩnh để lấy các tiêu đề cần thiết cho việc gửi yêu cầu API
                messageBody     // Nội dung của thông báo
        ).enqueue(new Callback<String>() {      // Xử lý kết quả của cuộc gọi API theo cách bất đồng bộ
            // Kiểm tra xem phản hồi từ API có thành công không
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {      // Kiểm tra cuộc gọi API thành công hay không
                    try {
                        JSONObject responseJson = new JSONObject(response.body());      // Chứa dữ liệu gửi về từ API
                        JSONArray results = responseJson.getJSONArray("results");       // Chứa kết quả liên quan đến việc gửi thông báo
                        if (responseJson.getInt("failure") == 1) {      // Nếu responseJson thì việc gửi thông báo lỗi
                            JSONObject error = (JSONObject) results.get(0);     // Chứa thông tin về lỗi liên quan đến thông báo
                            showToast(error.getString("error"));    // Thông báo về lỗi
                            return;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    showToast("Error: " + response.code());
                }
            }

            // Nếu gọi API không thành công, hiển thị thông báo lỗi
            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    // Tạo listener cho trạng thái hiện diện cho người nhận tin nhắn
    private void listenerAvailabilityOfReceiver() {
        // Xác định người dùng trong CSDL đang listener
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {    // Thêm một listener vào Firestore
            // Kiểm tra có lỗi xãy ra hay không, nếu có thì không thực hiện thay đổi nào
            if (error != null) {
                return;
            }
            if (value != null) {    // Nếu value != null thì thực hiện kiểm tra và cập nhật thông tin
                if (value.getLong(Constants.KEY_AVAILABILITY) != null) {    // Lấy giá trị trường "availability" từ tài liệu người nhận
                    // Nếu "availability" = 1 thì cập nhật isReceiverAvailable là true
                    int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY)).intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);      // Cập nhật token của người dùng để có thể gửi thông báo sau này
                if (receiverUser.image == null) {
                    // Cập nhật hình ảnh của người nhận trong adapter và thông báo cho adapter biết rằng có dữ liệu được thêm vào
                    receiverUser.image = value.getString(Constants.KEY_IMAGE);
                    chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                    chatAdapter.notifyItemRangeInserted(0, chatMessages.size());
                }
            }
            // Kiểm tra người dùng đang online hay không và hiển thị lên giao diện
            if (isReceiverAvailable) {
                binding.textAvailability.setVisibility(View.VISIBLE);
            } else {
                binding.textAvailability.setVisibility(View.GONE);
            }
        });
    }

    // Thiết lập listen sự kiện trên tài liệu trong bảng chat để listen các tin nhắn được gửi giữa người dùng hiện tại và người nhận tin nhắn
    private void listenMessage() {
        // Truy cập vào bảng chat trong Firestore
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))  // Lắng nghe tin nhắn từ người dùng đến người nhận
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);    // Lắng nghe sự kiện dựa trên kết quả trả về từ truy vấn
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)     // Lắng nghe tin ngắn gửi từ người nhận đến người dùng
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    // Xử lý sự kiện khi có thay đổi trong kết quả truy vấn
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        // Kiểm tra lỗi khi thực thi truy vấn
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            // Duyệt các thay đỗi của dữ liệu trong kết quả truy vấn
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {    // Nếu có tin nhắn mới được thêm vào
                    // Lấy thông tin thay đổi sau đó thêm vào danh sách chatMessage
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(chatMessage);
                }
            }
            // Sắp xếp đúng theo thứ tự thời gian
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {   // Ktra danh sách ban đầu có rỗng hay không
                chatAdapter.notifyDataSetChanged();     // Thông báo cho adapter cập nhật toàn bộ danh sách
            } else {
                // Nếu ds ban đầu không rỗng
                // Thông báo cho adapter biết rằng có tin nhắn mới được thêm vào và cần cập nhật giao diện tại vị trí thích hợp
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);   // Cuộn màn hình đến cuối để hiển thị tin nhắn mới nhất
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);   // Hiển thị tin nhắn
        }
        binding.progressBar.setVisibility(View.GONE);   // Ẩn progressBar khi đã xử lý xong
        // Kiểm tra đã tồn tại cuộc trò chuyện hay chưa
        if (conversionId == null) {
            checkForConversion();   // Kiểm tra và tạo cuộc trò chuyện nếu cần
        }
    };

    // Chuyển đổi ảnh thành giá trị chuỗi Base64
    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else  {
            return null;
        }
    }

    // Tải thông tin chi tiết của người nhận và hiển thị lên giao diện
    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);     // Đọc dữ liệu người nhận từ intent truyền qua từ Activity trước
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        // Kiểm tra xem inputMessage đã có tin nhắn hay chưa, nếu inputMassage rỗng thì không thể gửi được tin nhắn
        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.length() > 0) {
                    binding.layoutSend.setOnClickListener(v -> sendMessage());
                } else {
                    binding.layoutSend.setOnClickListener(null);
                }
            }
        });
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("dd MMMM, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    // Thêm thông tin trò chuyện mới vào Firestore
    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)     // Truy cập vào bảng lưu thông tin cuộc trò chuyện
                .add(conversion)    // Thêm tài liệu mới vào bảng
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());   // Sự kiện thành công khi thêm tài liệu vào Firestore
    }

    // Cập nhật thông tin cuộc trò chuyện đã tồn tại
    private void updateConversion(String message) {
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);   // Tham chiếu đến cuộc trò chuyện cần cập nhật
        documentReference.update(       // Cập nhật các trường dữ liệu của tài liệu
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    // Kiểm tra đã tồn tại cuộc trò chuyện giữa 2 người hay chưa
    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            // Kiểm tra từ 2 phía
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),     // Lấy ID người dùng
                    receiverUser.id     // Lấy ID người nhận
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    // Kiểm tra cuộc trò chuyện giữa 2 người dùng từ xa trong Firestore
    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()      // Gửi truy vấn đến Firestore
                .addOnCompleteListener(conversionOnCompleteListener);      // Thêm listener sự kiện hoàn tất vào truy vấn
    }

    // Xử lý kết quả của truy vấn kiểm tra cuộc trò chuyện giữa hai người dùng
    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);     // Lấy tài liệu từ kết quả truy vấn
            conversionId = documentSnapshot.getId();    // Gán ID
        }
    };

    private void setInfo() {
        String receiverUserId = receiverUser.id;    // Lấy id của người nhận tin nhắn
        Intent intent = new Intent(this, InfoActivity.class);
        intent.putExtra(Constants.KEY_RECEIVER_ID, receiverUserId);     // Đưa id người nhận tin nhắn sang InfoActivity
        binding.imageInfo.setOnClickListener(v -> {
            startActivity(intent);
        });
    }

    // Kiểm tra người dùng đang online hay không
    @Override
    protected void onResume() {
        super.onResume();
        listenerAvailabilityOfReceiver();
    }

    @Override
    public void onConversionClicked(User user) {

    }
}