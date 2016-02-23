package net.nashlegend.sourcewall.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by NashLegend on 16/2/23.
 */
public class Image implements Parcelable {
    private String url = "";
    private int width = 0;
    private int height = 0;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.url);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
    }

    public Image() {
    }

    protected Image(Parcel in) {
        this.url = in.readString();
        this.width = in.readInt();
        this.height = in.readInt();
    }

    public static final Parcelable.Creator<Image> CREATOR = new Parcelable.Creator<Image>() {
        public Image createFromParcel(Parcel source) {
            return new Image(source);
        }

        public Image[] newArray(int size) {
            return new Image[size];
        }
    };
}
