package com.deqiang.sudosolver;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private class Element{
        private int _value;
        private int _step;
        public Element(int value){
            this._value = value;
            this._step = -1;        //-1:invalid;0:preset;
        }
        public void setValue(int value){
            //myassert(value<SCALE,"");
            this._value = value;
        }
        public void setInvalidValue(){
            this._value = mask[SCALE];
        }
        public int getValue(){
            return _value;
        }
        public void setStep(int step){
            this._step = step;
        }
        public int getStep(){
            return this._step;
        }
    }

    private final static int MSG_INPUT=0;
    private final static int     MSG_SELECTGATE = 1;

    private int mStep=0;        //record current step number
    private int mPosition=0;

    private final static String TAG = "SUDO";
    private final static int CELL_SIZE=3;
    private final static int SCALE=CELL_SIZE*CELL_SIZE;
    private GridView gridViewDisplay = null;
    private GridView gridViewInput = null;
    private List<Map<String, String>> listItems_display = null;
    private List<Map<String, String>> listItems_input = null;

    private Element[][] maze=new Element[SCALE][SCALE];
    private int[] mask=new int[SCALE+1];
    private Element[][] maze_p=new Element[3*SCALE][SCALE];

    SimpleAdapter simpleAdapter_display=null;

    private Handler mHander=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridViewDisplay = findViewById(R.id.gridViewCube);
        gridViewInput = findViewById(R.id.gridViewInput);

        listItems_display = new ArrayList<Map<String,String>>();
        for(int i=0;i<81;i++){
            Map<String,String> item = new HashMap<>();
            item.put("num","");
            item.put("step","");
            listItems_display.add(item);
        }

        simpleAdapter_display = new SimpleAdapter(this, listItems_display,R.layout.cell,new String[]{"num","step"},new int[]{R.id.textViewNum,R.id.textViewStep});
        gridViewDisplay.setAdapter(simpleAdapter_display);
        gridViewDisplay.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int x=position/SCALE;
                int y=position%SCALE;

                mPosition = position;
                view.setBackgroundColor(Color.RED);
            }
        });
        gridViewDisplay.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return false;
            }
        });

        listItems_input = new ArrayList<Map<String,String>>();
        for(int i=1;i<10;i++){
            Map<String,String> item = new HashMap<>();
            item.put("num",new Integer(i).toString());
            listItems_input.add(item);
        }
        String[] oprs={"删除","","单步","<",">"};
        for(int i=0;i<oprs.length;i++){
            Map<String,String> item = new HashMap<>();
            item.put("num",oprs[i]);
            listItems_input.add(item);
        }

        SimpleAdapter simpleAdapter_input = new SimpleAdapter(this, listItems_input,R.layout.cell,new String[]{"num"},new int[]{R.id.textViewNum});
        gridViewInput.setAdapter(simpleAdapter_input);
        gridViewInput.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Message message = mHander.obtainMessage(MSG_INPUT,position,0);
                mHander.sendMessage(message);
            }
        });
        gridViewInput.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return false;
            }
        });

        mHander = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SELECTGATE:
                        handle_selectGate((String)msg.obj);
                        break;
                    case MSG_INPUT:
                        handle_input(msg.arg1);
                        break;
                    default:
                        break;
                }
            }
        };

        init();
    }

    public void setDisplayOnMaze(){
        for(int i=0;i<SCALE;i++)
            for(int j=0;j<SCALE;j++){
                Map<String,String> item = listItems_display.get(i*SCALE+j);
                if(maze[i][j].getValue()>0){
                    item.put("num","");
                    item.put("step","");
                }else{
                    item.put("num",new Integer(-maze[i][j].getValue()+1).toString());
                    item.put("step",new Integer(maze[i][j].getStep()).toString());
                }
            }
        simpleAdapter_display.notifyDataSetChanged();
    }

    public void clear(View view){
        reinit();
        setDisplayOnMaze();
    }
    public void compute(View view){
        int counter=0;
        boolean flag;
        String memo=null;
        long duration = System.currentTimeMillis();
        TextView textView = (TextView) findViewById(R.id.textViewMemo);

        for(int i=0;i<SCALE;i++)
            for(int j=0;j<SCALE;j++) {
                int position = i * SCALE + j;
                Map<String, String> item = listItems_display.get(position);
                String value = item.get("num");
                if (value.equals("")) {
                    maze[i][j].setInvalidValue();
                    maze[i][j].setStep(-1);
                } else {
                    maze[i][j].setValue(-Integer.parseInt(value) + 1);
                    maze[i][j].setStep(0);
                }
            }
        mStep = 1;

        do{
            process();
            //printMaze();
            counter++;
            //setDisplayOnMaze();
        }while(!(flag=isSolved()) && counter<15);

        duration = System.currentTimeMillis() - duration;

        setDisplayOnMaze();

        if(flag) {
            memo = String.format("Done! 迭代%d次 耗时%d毫秒",counter,duration);
        }else{
            memo = String.format("Fail! 迭代%d次 耗时%d毫秒",counter,duration);
        }
        textView.setText(memo);
    }

    private boolean load(String filename)
    {
        reinit();

        try {
            InputStream in = getAssets().open(filename);
            InputStreamReader inputreader = new InputStreamReader(in,"GB2312");
            BufferedReader reader = new BufferedReader(inputreader);
            String line;
            while(null != (line = reader.readLine())) {
                int x = 0, y = 0, v = 0;
                if(line.equals("") || line.startsWith("/"))
                    continue;

                String[] segs = line.split(",");
                if (segs.length != 3) {
                    Log.e(TAG, String.format("%s doesn't not have 3 segs", line));
                    return false;
                }
                x = Integer.parseInt(segs[0]);
                y = Integer.parseInt(segs[1]);
                v = Integer.parseInt(segs[2]);
                //check validation
                if ((x >= SCALE) || (y >= SCALE) || (v >= SCALE)) {
                    Log.e(TAG, String.format("%d %d %d each should be less than %d", x, y, v, SCALE));
                    return false;
                }

                maze[x][y].setValue(-v);
            }
        }catch(IOException e){
            e.printStackTrace();
        }

        return true;
    }

    //init all the data
    //only run once at the begining of app startup
    void init()
    {
        int i,j,m,n;
        int counter;
        int x,y;

        j=1;
        mask[0]=1;
        for(i=1;i<SCALE;i++){
            j *=2;
            mask[i]=j;
        }
        mask[SCALE] = (2<<(SCALE-1))-1;

        for(i=0;i<SCALE;i++)
            for(j=0;j<SCALE;j++)
                maze[i][j] = new Element(mask[SCALE]);

        counter=0;
        for(i=0;i<SCALE;i++)
        {
            for(j=0;j<SCALE;j++)
                maze_p[counter][j] = maze[i][j];
            counter++;
        }

        for(i=0;i<SCALE;i++)
        {
            for(j=0;j<SCALE;j++)
                maze_p[counter][j] = maze[j][i];
            counter++;
        }

        for(i=0;i<CELL_SIZE;i++)
            for(j=0;j<CELL_SIZE;j++)
            {
                for(m=0;m<CELL_SIZE;m++)
                    for(n=0;n<CELL_SIZE;n++)
                    {
                        int t = m*CELL_SIZE+n;
                        x = i*CELL_SIZE + m;
                        y = j*CELL_SIZE + n;
                        maze_p[counter][t] = maze[x][y];
                    }

                counter++;
            }
    }
    //run when reply
    void reinit(){
        for(int i=0;i<SCALE;i++)
            for(int j=0;j<SCALE;j++) {
                maze[i][j].setInvalidValue();
                maze[i][j].setStep(-1);
            }
    }

    private void printB(int value){
        for(int i=0;i<32;i++){
            int t=(value & 0x80000000>>>i)>>>(31-i);
            System.out.print(t);
        }
    }

    void printMaze()
    {
        int i,j;
        for(i=0;i<SCALE;i++)
        {
            for(j=0;j<SCALE;j++)
            {
                String str = maze[i][j].getValue()>0?"-":new Integer(-maze[i][j].getValue()+1).toString();
                System.out.print(str);
            }
            System.out.print("\n");
        }

        System.out.print("\n");
    }

    boolean isSolved()
    {
        int i,j;

        for(i=0;i<SCALE;i++)
            for(j=0;j<SCALE;j++)
                if(maze[i][j].getValue()>0)
                    return false;

        return true;
    }
    //
    void statistics(Element[] number_static, int value, Element[] array, int num)
    {
        int i=0;
        while(i<SCALE)
        {
            if(1==value %2){
                int temp = number_static[i].getValue();
                number_static[i].setValue(temp+1);

                array[i].setValue(num);     //use array to remember the location of the number i
            }

            i++;
            value = value / 2;
        }
    }

    //求解：根据已知内容，仅进行一轮迭代
    void process()
    {
        int i,j;
        int result;
        Element element=null;
        Element[] stat=new Element[SCALE];
        Element[] loc_marker=new Element[SCALE];

        for(i=0;i<3*SCALE;i++)
        {
            //get the mask for the 9 elements
            result=0;
            for(j=0;j<SCALE;j++)
            {
                element = maze_p[i][j];
                if(element.getValue()<=0)
                    result |= mask[-element.getValue()];
            }
            //apply the mask to each >0 element
            for(j=0;j<SCALE;j++)
            {
                element = maze_p[i][j];
                if(element.getValue()>0) {
                    int temp = element.getValue() & ~result;
                    element.setValue(temp);
                }
            }
            //statistics
            for(j=0;j<SCALE;j++)
            {
                stat[j] = new Element(0);
                loc_marker[j] = new Element(0);
            }
            for(j=0;j<SCALE;j++)
            {
                element = maze_p[i][j];
                if(element.getValue()>0)
                {//to statistics
                    statistics(stat, element.getValue(), loc_marker, j);
                }
                else
                {
                    stat[-element.getValue()].setValue(0);  //means number of j has been decided
                }
            }
            //check statistics results
            for(j=0;j<SCALE;j++)
            {
                if(1 == stat[j].getValue())
                {//find answer for number of j
                    maze_p[i][loc_marker[j].getValue()].setValue(-j);
                    maze_p[i][loc_marker[j].getValue()].setStep(mStep++);
                }
            }
        }
    }

    int main()
    {
        int counter=0;
        init();
        printMaze();

        do{
            process();
            printMaze();
            counter++;
            setDisplayOnMaze();
        }while(!isSolved());

        Log.d(TAG, String.format("Counter=%d\n",counter));

        return 0;
    }

    //value=-1 - 8
    // -1:delete the value of the cell
    private void setCell(int position, int value){
        myassert(position<SCALE*SCALE,"");
        myassert(value<SCALE,"");

        int x=position/SCALE;
        int y=position%SCALE;

        View view = gridViewDisplay.getChildAt(position);
        view.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
        Map<String,String> item = listItems_display.get(position);
        if(-1==value){
            item.put("num", "");
        }else {
            item.put("num", new Integer(value).toString());
        }

        simpleAdapter_display.notifyDataSetChanged();
    }

    public void handle_input(int input){
        switch (input){
            case 9://delete
                setCell(mPosition,-1);
                break;
            case 10: //null
                break;
            case 11: //singleStep
                for(int i=0;i<SCALE;i++)
                    for(int j=0;j<SCALE;j++){
                        Map<String,String> item = listItems_display.get(i*SCALE+j);
                        if((maze[i][j].getValue()>0) || (maze[i][j].getStep()!=0)){
                            item.put("num","");
                            item.put("step","");
                        }else{
                            item.put("num",new Integer(-maze[i][j].getValue()+1).toString());
                            item.put("step",new Integer(maze[i][j].getStep()).toString());
                        }
                    }
                simpleAdapter_display.notifyDataSetChanged();
                mStep=0;
                break;
            case 12: //backward
            {
                int x=0,y=0;
                int i=0;
                boolean ifavailable=false;
                if (0 == mStep)
                    return;
                for(i=0;i<SCALE*SCALE;i++){
                    x=i/SCALE;
                    y=i%SCALE;
                    if(mStep==maze[x][y].getStep()) {
                        ifavailable = true;
                        break;
                    }
                }

                if(ifavailable){
                    mStep--;
                    setCell(i,-1);
                }
            }
                break;
            case 13://forward
            {
                int i=0,x=0,y=0;
                boolean ifavailable=false;
                mStep++;
                if (mStep>=SCALE*SCALE)
                    return;
                for(i=0;i<SCALE*SCALE;i++) {
                    x = i / SCALE;
                    y = i % SCALE;
                    if(mStep==maze[x][y].getStep()) {
                        ifavailable = true;
                        break;
                    }
                }
                if(ifavailable){
                    setCell(i,-maze[x][y].getValue()+1);
                }
            }
                break;
            default:
                setCell(mPosition,input+1);
                break;
        }
    }
    public void handle_selectGate(String file){
        if(load(file)){
            setDisplayOnMaze();
        }else{
            Toast.makeText(this,String.format("Load %s failed",file),Toast.LENGTH_SHORT);
        };
    }

    public void load(View view){
        try{
            String all[] = getAssets().list("");
            ArrayList<String> array = new ArrayList<String>();
            for(int i=0;i<all.length;i++){
                if(all[i].endsWith(".gate"))
                    array.add(all[i]);
            }
            final String[] filenames = new String[array.size()];
            array.toArray(filenames);
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("Please select a gate")
                    .setSingleChoiceItems(filenames, 1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Message message = mHander.obtainMessage(MSG_SELECTGATE,filenames[which]);
                            mHander.sendMessage(message);
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton("Cancel",null)
                    .create()
                    .show();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

public void singleStep(View view){

}
    private void myassert(boolean flag, String str){
        try{
            if(!flag){
                throw new Exception(str);
            }
        }catch (Exception e){

        }
    }

}
