package cc.lovezhy.raft.rpc.kryo.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.List;

public class Student {
    private String name;
    private Integer age;
    private List<School> schoolList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public List<School> getSchoolList() {
        return schoolList;
    }

    public void setSchoolList(List<School> schoolList) {
        this.schoolList = schoolList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Student student = (Student) o;
        return Objects.equal(name, student.name) &&
                Objects.equal(age, student.age) &&
                Objects.equal(schoolList, student.schoolList);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, age, schoolList);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("age", age)
                .add("schoolList", schoolList)
                .toString();
    }
}
