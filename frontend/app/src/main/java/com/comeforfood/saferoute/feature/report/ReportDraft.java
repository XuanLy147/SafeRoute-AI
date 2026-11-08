package com.comeforfood.saferoute.feature.report;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.comeforfood.saferoute.feature.shared.Point;
/** Local UI state only. No submission or verification is implied. */
public class ReportDraft extends ViewModel {
    private final SavedStateHandle state;
    public ReportDraft(SavedStateHandle state) { this.state=state; }
    public void clear() { for(String key:new java.util.HashSet<>(state.keys())) state.remove(key); }
    public int step() { Integer v=state.get("step"); return v==null?0:v; }
    public void step(int v) { state.set("step",v); }
    public String image() { return state.get("image"); }
    public String modelVersion(){return state.get("modelVersion");}
    public void modelVersion(String value){state.set("modelVersion",value);}
    public String uploadedId(){return state.get("uploadedId");}
    public void uploadedId(String id){state.set("uploadedId",id);}
    public boolean uncertain(){return Boolean.TRUE.equals(state.get("uncertain"));}
    public void uncertain(boolean value){state.set("uncertain",value);}
    public void detections(java.util.List<com.comeforfood.saferoute.core.model.DetectionDto> value){state.set("detections",new java.util.ArrayList<>(value));}
    public java.util.ArrayList<com.comeforfood.saferoute.core.model.DetectionDto> detections(){java.util.ArrayList<com.comeforfood.saferoute.core.model.DetectionDto> v=state.get("detections");return v==null?new java.util.ArrayList<>():v;}
    public void image(String uri) { state.remove("uploadedId");state.remove("detections");state.remove("modelVersion"); state.set("image",uri); state.set("confirmed",false); }
    public String camera() { return state.get("camera"); }
    public void camera(String uri) { state.set("camera",uri); }
    public Point point() { Double lat=state.get("lat"),lon=state.get("lon"); return lat==null||lon==null?null:new Point(lat,lon); }
    public void point(Point p) { state.set("lat",p.latitude);state.set("lon",p.longitude);state.remove("locationLabel");state.set("confirmed",false); }
    public String locationLabel() { return state.get("locationLabel"); }
    public void locationLabel(String value) { state.set("locationLabel",value); }
    public int category() {
        Integer value=state.get("category"),schema=state.get("categorySchema");
        if(schema==null && value!=null && value>=0) {
            // Version 1 used D00,D10,D20,D40,Repair,flood,obstacle.
            // Repair has no equivalent in the new UI: require an explicit new choice.
            int[] migrated={0,0,0,1,-1,2,3};
            value=value<migrated.length?migrated[value]:-1;
            state.set("category",value);state.set("confirmed",false);
        }
        state.set("categorySchema",2);
        return value==null?-1:value;
    }
    public void category(int v) { if(category()!=v)state.set("confirmed",false);state.set("categorySchema",2);state.set("category",v); }
    public int severity() { Integer v=state.get("severity");return v==null?1:v; }
    public void severity(int v) { if(severity()!=v)state.set("confirmed",false);state.set("severity",v); }
    public String description() { String v=state.get("description");return v==null?"":v; }
    public void description(String v) { if(!description().equals(v))state.set("confirmed",false);state.set("description",v); }
    public boolean confirmed() { return Boolean.TRUE.equals(state.get("confirmed")); }
    public void confirmed(boolean v) { state.set("confirmed",v); }
}
