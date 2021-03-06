package net.nashlegend.sourcewall.model;

import android.os.Parcel;

/**
 * Created by NashLegend on 2014/10/31 0031
 */
public class SubItem extends AceModel {

    public static final int Type_Collections = 0;//集合，如科学人、热贴、精彩问答、热门问答
    public static final int Type_Single_Channel = 1;//单项
    public static final int Type_Private_Channel = 2;//私人频道，我的小组
    public static final int Type_Subject_Channel = 3;//科学人学科频道

    public static final int Section_Article = 0;
    public static final int Section_Post = 1;
    public static final int Section_Question = 2;

    private int section;
    private int type;
    private String name = "";
    private String value = "";

    public SubItem(int section, int type, String name, String value) {
        setSection(section);
        setType(type);
        setName(name);
        setValue(value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getSection() {
        return section;
    }

    public void setSection(int section) {
        this.section = section;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SubItem) {
            SubItem sb = (SubItem) o;
            return sb.getName().equals(getName()) && sb.getSection() == getSection() && sb.getType() == getType() && sb.getValue().equals(getValue());
        }
        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.section);
        dest.writeInt(this.type);
        dest.writeString(this.name);
        dest.writeString(this.value);
    }

    protected SubItem(Parcel in) {
        this.section = in.readInt();
        this.type = in.readInt();
        this.name = in.readString();
        this.value = in.readString();
    }

    public static final Creator<SubItem> CREATOR = new Creator<SubItem>() {
        public SubItem createFromParcel(Parcel source) {
            return new SubItem(source);
        }

        public SubItem[] newArray(int size) {
            return new SubItem[size];
        }
    };
}
