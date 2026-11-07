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
    public void image(String uri) { state.set("image",uri); state.set("confirmed",false); }
    public String camera() { return state.get("camera"); }
    public void camera(String uri) { state.set("camera",uri); }
    public Point point() { Double lat=state.get("lat"),lon=state.get("lon"); return lat==null||lon==null?null:new Point(lat,lon); }
    public void point(Point p) { state.set("lat",p.latitude);state.set("lon",p.longitude);state.set("confirmed",false); }
    public int category() { Integer v=state.get("category");return v==null?-1:v; }
    public void category(int v) { if(category()!=v)state.set("confirmed",false);state.set("category",v); }
    public int severity() { Integer v=state.get("severity");return v==null?1:v; }
    public void severity(int v) { if(severity()!=v)state.set("confirmed",false);state.set("severity",v); }
    public String description() { String v=state.get("description");return v==null?"":v; }
    public void description(String v) { if(!description().equals(v))state.set("confirmed",false);state.set("description",v); }
    public boolean confirmed() { return Boolean.TRUE.equals(state.get("confirmed")); }
    public void confirmed(boolean v) { state.set("confirmed",v); }
}
