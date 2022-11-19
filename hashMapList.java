import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class hashMapList {

    public static void main(String[] args) {
        Map<String,Object> map=new HashMap<>();

        User user1 = new User("1", "张三", "男");
        map.put("user1",user1 );
    }
}
