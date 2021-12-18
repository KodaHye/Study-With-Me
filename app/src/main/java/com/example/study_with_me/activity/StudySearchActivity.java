package com.example.study_with_me.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.study_with_me.R;
import com.example.study_with_me.adapter.SearchAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.safetynet.SafetyNetAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StudySearchActivity extends AppCompatActivity {
    private String userID;
    private FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
    private DatabaseReference databaseReference = firebaseDatabase.getReference();
    private DatabaseReference studyGroupRef = databaseReference.child("studygroups");
    private ListView studySearchListView;

    private ArrayList<Map<String, Object>> studyList = new ArrayList<>(); // 전체 스터디 리스트
    private ArrayList<Map<String, Object>> filteredStudyList = studyList; // 필터링된 스터디 리스트

    private ArrayList<Map<String, Object>> filterTypeList = new ArrayList<>();
    private ArrayList<Map<String, Object>> filterCountList = new ArrayList<>();
    private ArrayList<Map<String, Object>> filterDateList = new ArrayList<>();

    private boolean noCountClick = true, noDateClick = true;
    private boolean isFirstVisit = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.study_search_main);

        studySearchListView = (ListView) findViewById(R.id.studySearchListView);

        if(firebaseAuth.getCurrentUser() != null){
            userID = firebaseAuth.getCurrentUser().getUid();
            FirebaseApp.initializeApp(this);
            FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
            firebaseAppCheck.installAppCheckProviderFactory(
                    SafetyNetAppCheckProviderFactory.getInstance());
        }
        // 상단 메뉴바
        getSupportActionBar().setTitle("스터디 검색");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /** floatingActionButton 누르면 스터디 생성 화면 **/
        floatingButtonClickedListener();

        /** 펼치기 버튼(expandButton) 클릭 시 필터링 검색 창 펼침 **/
        expandButtonClickedListener();

        /** DB의 studygroups의 변화가 생겼을 때 감지하는 listener **/
        setStudyGroupsChangedListener();

        EditText editText = (EditText)findViewById(R.id.editText) ;
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable edit) {
                String filterText = edit.toString();
                if(filterText.length() > 0) {
                    studySearchListView.setFilterText(filterText);
                } else {
                    studySearchListView.clearTextFilter();
                }
            }
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!isFirstVisit)
            setStudyGroupsChangedListener();
        isFirstVisit = false;
    }

    /** 액션바 오버라이딩 **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void filteringListView() {
        Set<Map<String, Object>> typeSet = new HashSet<>(filterTypeList);
        Set<Map<String, Object>> countSet = new HashSet<>(filterCountList);
        Set<Map<String, Object>> dateSet = new HashSet<>(filterDateList);

        if(!noCountClick)
            typeSet.retainAll(countSet);
        if(!noDateClick)
            typeSet.retainAll(dateSet);

        ArrayList<Map<String, Object>> list = new ArrayList<>(typeSet);
        setListView(list);
    }

    /** 스터디 검색화면 리스트 뷰 처리 **/
    private void setListView(ArrayList<Map<String, Object>> list) {
        SearchAdapter adapter = new SearchAdapter(this, list);
        studySearchListView.setAdapter(adapter);
        studySearchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Map<String, Object> item = (Map<String, Object>) adapter.getItem(position);
                Intent intent = new Intent(StudySearchActivity.this, StudyPostActivityMessage.class);
                intent.putExtra("studyGroup", (Serializable) item);
                startActivity(intent);
            }
        });
    }

    /** DB에 있는 모든 스터디 정보 가져오는 함수 **/
    private void collectAllStudyGroups(Map<String, Object> studygroups) {
        for(Map.Entry<String, Object> entry : studygroups.entrySet()) {
            Map singleStudyGroup = (Map) entry.getValue();
            if(!(boolean)singleStudyGroup.get("closed")){
                studyList.add(singleStudyGroup);
            }
        }
    }

    /** DB의 studygroups에 무언가 추가되거나 변경될 시 처리하는 함수 **/
    private void setStudyGroupsChangedListener() {
        studyList.clear();
        studyGroupRef.addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(snapshot.getValue() != null) {
                            collectAllStudyGroups((Map<String, Object>) snapshot.getValue());
                            setListView(studyList);
                            filteredStudyList = (ArrayList<Map<String, Object>>) studyList.clone();

                            // 스터디 목록 없으면 addMessage("스터디를 등록해주세요")출력
                            if(studyList.size() != 0) {
                                findViewById(R.id.addMessage).setVisibility(View.INVISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                }
        );
    }

    /** floatingButton 처리 함수 **/
    private void floatingButtonClickedListener() {
        FloatingActionButton floatingActionButton = (FloatingActionButton)findViewById(R.id.floatingActionButton);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), StudyRegisterActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);
            }
        });
    }

    /** 펼치기 버튼 처리 함수 **/
    private void expandButtonClickedListener() {
        ImageView expandButton = (ImageView)findViewById(R.id.expandButton);
        LinearLayout filteringScreen = (LinearLayout)findViewById(R.id.filteringScreen);
        expandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (filteringScreen.getVisibility() == View.VISIBLE) {
                    filteringScreen.setVisibility(View.GONE);
                } else {
                    filteringScreen.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /** 상단 바 마이페이지, 알림 버튼 **/
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.alarmBell:
                Intent intent1 = new Intent(this, AlarmActivity.class);
                startActivity(intent1);
                return true;
            case R.id.myPage:
                Intent intent2 = new Intent(this, MyPageActivity.class);
                startActivity(intent2);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** 분류 필터링 **/
    public void onClick(View v) {
        filterTypeList.clear();

        switch (v.getId()) {
            case R.id.all:
                Toast.makeText(getApplicationContext(),"전체 스터디", Toast.LENGTH_SHORT).show();
                filterTypeList = (ArrayList<Map<String, Object>>) filteredStudyList.clone();
                break;
            case R.id.programming:
                Toast.makeText(getApplicationContext(), "프로그래밍만 분류", Toast.LENGTH_SHORT).show();
                for (Map<String, Object> sg : filteredStudyList) {
                    if(String.valueOf(sg.get("type")).equals("프로그래밍")) {
                        filterTypeList.add(sg);
                    }
                }
                break;
            case R.id.employ:
                Toast.makeText(getApplicationContext(), "취업만 분류", Toast.LENGTH_SHORT).show();
                for (Map<String, Object> sg : filteredStudyList) {
                    if(String.valueOf(sg.get("type")).equals("취업")) {
                        filterTypeList.add(sg);
                    }
                }
                break;
            case R.id.language:
                Toast.makeText(getApplicationContext(), "어학 분류", Toast.LENGTH_SHORT).show();
                for (Map<String, Object> sg : filteredStudyList) {
                    if(String.valueOf(sg.get("type")).equals("어학")) {
                        filterTypeList.add(sg);
                    }
                }
                break;
            case R.id.ect:
                Toast.makeText(getApplicationContext(), "기타만 분류", Toast.LENGTH_SHORT).show();
                for (Map<String, Object> sg : filteredStudyList) {
                    if (!String.valueOf(sg.get("type")).equals("프로그래밍")  && !String.valueOf(sg.get("type")).equals("취업") && !String.valueOf(sg.get("type")).equals("어학")) {
                        filterTypeList.add(sg);
                    }
                }
                break;
        }
        filteringListView();
    }

    // 각각 글에 맞는 글이 매치
    public void studyAreaClicked(View view) {
        Intent intent = new Intent(this, StudyPostActivityMessage.class);
        startActivity(intent);
    }

    /**인원수 필터링**/
    public void filterCount(View view) {
        noCountClick = false;
        filterCountList.clear();
        switch (view.getId()) {
            case R.id.two:
                for (Map<String, Object> sg : filteredStudyList) {
                    if (Integer.valueOf(String.valueOf(sg.get("member"))) == 2) {
                        filterCountList.add(sg);
                    }
                }
                break;
            case R.id.three:
                for (Map<String, Object> sg : filteredStudyList) {
                    if (Integer.valueOf(String.valueOf(sg.get("member"))) == 3) {
                        filterCountList.add(sg);
                    }
                }
                break;
            case R.id.four:
                for (Map<String, Object> sg : filteredStudyList) {
                    if (Integer.valueOf(String.valueOf(sg.get("member"))) == 4) {
                        filterCountList.add(sg);
                    }
                }
                break;
            case R.id.moreFour:
                for (Map<String, Object> sg : filteredStudyList) {
                    if (Integer.valueOf(String.valueOf(sg.get("member"))) > 4) {
                        filterCountList.add(sg);
                    }
                }
                break;
        }
        filteringListView();
    }

    /** 두 날짜 사이의 기간 구하기 **/
    private int getDiffMonth(String sDate, String eDate) {
        int y1 = Integer.parseInt(sDate.substring(0, 4));
        int y2 = Integer.parseInt(eDate.substring(0, 4));
        int m1 = Integer.parseInt(sDate.substring(5, 7));
        int m2 = Integer.parseInt(eDate.substring(5, 7));

        int diffMonth = (y2-y1) * 12 + (m2-m1);
        return diffMonth;
    }

    /**스터디 기간 필터링**/
    public void filerDate(View view) {
        String startDate;
        String endDate;
        int month;
        filterDateList.clear();
        noDateClick = false;
        switch(view.getId()) {
            case R.id.oneMonth:
                for(Map<String, Object> sg: filteredStudyList) {
                    startDate = String.valueOf(sg.get("startDate"));
                    endDate = String.valueOf(sg.get("endDate"));

                    month = getDiffMonth(startDate, endDate);
                    if(month == 1) {
                        filterDateList.add(sg);
                    }
                }
                break;
            case R.id.twoMonth:
                for(Map<String, Object> sg: filteredStudyList) {
                    startDate = String.valueOf(sg.get("startDate"));
                    endDate = String.valueOf(sg.get("endDate"));

                    month = getDiffMonth(startDate, endDate);
                    if(month == 2) {
                        filterDateList.add(sg);
                    }
                }
                break;
            case R.id.sixMonth:
                for(Map<String, Object> sg: filteredStudyList) {
                    startDate = String.valueOf(sg.get("startDate"));
                    endDate = String.valueOf(sg.get("endDate"));

                    month = getDiffMonth(startDate, endDate);
                    if(month == 6) {
                        filterDateList.add(sg);
                    }
                }
                break;
            case R.id.moreSixMonth:
                for(Map<String, Object> sg: filteredStudyList) {
                    startDate = String.valueOf(sg.get("startDate"));
                    endDate = String.valueOf(sg.get("endDate"));

                    month = getDiffMonth(startDate, endDate);
                    if(month != 1 && month != 2 && month != 6) {
                        filterDateList.add(sg);
                    }
                }
                break;
        }
        filteringListView();
    }
}