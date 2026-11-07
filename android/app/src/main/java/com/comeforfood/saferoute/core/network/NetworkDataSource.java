package com.comeforfood.saferoute.core.network;

import com.comeforfood.saferoute.core.model.ReportDto;
import com.comeforfood.saferoute.feature.shared.*;
import com.comeforfood.saferoute.feature.map.Hazard;
import com.comeforfood.saferoute.feature.routing.*;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import retrofit2.Call;
import retrofit2.Response;

public final class NetworkDataSource implements Tv1DataSource {
    private final ApiService api;
    public NetworkDataSource(ApiService api) { this.api=api; }
    @Override public Request loadReports(Callback<List<Hazard>> cb) {
        return send(api.reports(), cb, rows -> {
            List<Hazard> result=new ArrayList<>();
            for(ReportDto.ReportResponse r:rows) {
                Hazard.Status status=Hazard.Status.valueOf(r.getStatus());
                if(status==Hazard.Status.REJECTED)continue;
                Hazard.Type type;
                if("Nứt đường".equalsIgnoreCase(r.getCategoryUser())) type=Hazard.Type.CRACK;
                else try { type=Hazard.Type.valueOf(r.getCategoryUser().toUpperCase(Locale.ROOT)); }
                catch(IllegalArgumentException e) {type=Hazard.Type.MANUAL;}
                result.add(new Hazard(r.getId(),r.getCategoryUser(),r.getDescription(),new Point(r.getLatitude(),r.getLongitude()),type,status));
            }
            return result;
        });
    }
    @Override public Request compare(Point a, Point b, Callback<RouteComparison> cb) {
        JsonObject body=new JsonObject();Gson gson=new Gson();
        body.add("origin",gson.toJsonTree(a));body.add("destination",gson.toJsonTree(b));body.addProperty("travel_mode","TWO_WHEELER");
        Call<JsonObject> call=api.compare(body);
        return send(call,cb,NetworkDataSource::mapRoutes,new RouteComparison(Collections.emptyList(),null,"Không tìm thấy tuyến."));
    }
    public static RouteComparison mapRoutes(JsonObject body) {
        String status=string(body,"risk_assessment_status");
        if(!Arrays.asList("AVAILABLE","PARTIAL","UNAVAILABLE","INVALID").contains(status))throw new IllegalArgumentException("Missing risk status");
        if(!status.equals("AVAILABLE")&&string(body,"recommendation_basis").equals("SAFETY_LOWEST_RISK"))throw new IllegalArgumentException("Invalid recommendation basis");
        List<Route> routes=new ArrayList<>();
        for(JsonElement entry:body.getAsJsonArray("routes")) {
            JsonObject r=entry.getAsJsonObject();
            JsonObject risk=r.has("risk")&&!r.get("risk").isJsonNull()?r.getAsJsonObject("risk"):null;
            if(status.equals("INVALID")||status.equals("UNAVAILABLE"))risk=null;
            if(status.equals("AVAILABLE")&&risk==null)throw new IllegalArgumentException("Missing score");
            int verified=risk==null?0:risk.get("verified_hazards_count").getAsInt();
            int pending=risk==null?0:risk.get("pending_hazards_count").getAsInt();
            if(risk!=null && verified+pending!=risk.get("hazards_count").getAsInt())throw new IllegalArgumentException("Invalid counts");

            String displayReason;
            if (risk == null) {
                displayReason = "Chưa có đánh giá nguy cơ";
            } else {
                int total = verified + pending;
                if (total == 0) {
                    displayReason = "Tuyến đường thông thoáng, chưa ghi nhận nguy cơ mặt đường.";
                } else {
                    displayReason = "Phát hiện " + total + " điểm cảnh báo (" + verified + " đã xác minh"
                            + (pending > 0 ? ", " + pending + " chờ duyệt" : "") + ").";
                }
            }

            routes.add(new Route(string(r,"id"),string(r,"summary"),r.get("duration_seconds").getAsInt(),r.get("distance_meters").getAsInt(),
                risk==null?null:risk.get("score").getAsDouble(),verified,pending,displayReason,decode(string(r,"polyline"))));
        }
        String assessment="AVAILABLE".equals(status)?"Có đánh giá nguy cơ cho các tuyến"
            :"PARTIAL".equals(status)?"Chưa đủ dữ liệu để so sánh nguy cơ giữa các tuyến"
            :"Chưa có đánh giá nguy cơ khả dụng";
        String description=assessment+"\n"+string(body,"recommendation_reason")+"\n"+string(body,"disclaimer");
        String recommended=string(body,"recommended_route_id");
        return new RouteComparison(routes,recommended.isEmpty()?null:recommended,description);
    }
    private static String string(JsonObject o,String key) { return !o.has(key)||o.get(key).isJsonNull()?"":o.get(key).getAsString(); }
    public static List<Point> decode(String value) { return RouteGeometry.decodePolyline(value); }
    private static <T,R> Request send(Call<T> call,Callback<R> cb,Function<T,R> map) {
        return send(call,cb,map,null);
    }
    private static <T,R> Request send(Call<T> call,Callback<R> cb,Function<T,R> map,R notFound) {
        call.enqueue(new retrofit2.Callback<T>() {
            public void onResponse(Call<T> c,Response<T> r) {
                if(c.isCanceled())return;
                if(r.code()==404&&notFound!=null){cb.success(notFound);if(r.errorBody()!=null)r.errorBody().close();return;}
                if(r.isSuccessful()&&r.body()!=null) {
                    try { cb.success(map.apply(r.body())); }catch(RuntimeException e){cb.failure(Failure.INVALID_DATA);}
                }else cb.failure(r.code()==401?Failure.UNAUTHORIZED:r.code()==403?Failure.FORBIDDEN:r.code()==429?Failure.RATE_LIMITED:Failure.SERVER);
                if(r.errorBody()!=null)r.errorBody().close();
            }
            public void onFailure(Call<T> c,Throwable t) {if(!c.isCanceled())cb.failure(Failure.NETWORK);}
        });return call::cancel;
    }
}
