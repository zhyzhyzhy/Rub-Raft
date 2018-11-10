package cc.lovezhy.raft.rpc.kryo;

import cc.lovezhy.raft.rpc.kryo.model.School;
import cc.lovezhy.raft.rpc.kryo.model.Student;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class KryoTest {

    private static final Logger log = LoggerFactory.getLogger(KryoTest.class);

    private Student student;

    @Before
    public void setUp() {
        student = new Student();
        student.setName("zhuyichen");
        student.setAge(18);

        List<School> schoolList = Lists.newArrayList();
        schoolList.add(new School("江都中学"));
        schoolList.add(new School("南京邮电大学"));
        student.setSchoolList(schoolList);

        log.info("origin student={}", student);
    }

    @Test
    public void serializeTest() {
        Kryo kryo = new Kryo();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Output output = new Output(byteArrayOutputStream);
        kryo.writeObject(output, student);
        output.close();

        Input input = new Input(byteArrayOutputStream.toByteArray());
        Student student = kryo.readObject(input, Student.class);
        log.info("serialized student={}", student);
        Assert.assertEquals(this.student, student);
    }
}
