package br.com.operadorzero.venue;

import br.com.operadorzero.venue.VenueDtos.FieldResponse;
import br.com.operadorzero.venue.VenueDtos.MapResponse;
import br.com.operadorzero.venue.VenueDtos.SaveFieldRequest;
import br.com.operadorzero.venue.VenueDtos.SaveMapRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VenueRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public VenueRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<FieldResponse> fields(long userId, String q, String city, String state, int limit) {
        return jdbc.query("""
            SELECT * FROM airsoft_field WHERE status='ACTIVE' AND
              (:q='' OR lower(name) LIKE :query) AND (:city='' OR lower(city)=lower(:city)) AND
              (:state='' OR state_code=upper(:state)) ORDER BY lower(name) LIMIT :limit
            """, new MapSqlParameterSource().addValue("q", q).addValue("query", "%"+q.toLowerCase()+"%")
            .addValue("city", city).addValue("state", state).addValue("limit", limit), (r,i)->field(r,userId));
    }
    public Optional<FieldResponse> field(UUID id, long userId) {
        try { return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM airsoft_field WHERE public_id=:id AND status='ACTIVE'", Map.of("id",id),(r,i)->field(r,userId))); }
        catch (EmptyResultDataAccessException exception) { return Optional.empty(); }
    }
    public UUID createField(long userId, SaveFieldRequest r, String city, String state, Instant now) {
        return jdbc.queryForObject("""
          INSERT INTO airsoft_field(responsible_user_id,name,description,phone,contact_email,address_line,address_number,
          complement,district,city,state_code,postal_code,region,location_url,rules,opening_hours,amenities,maximum_capacity,
          average_price,payment_methods,created_at,updated_at) VALUES(:userId,:name,:description,:phone,:email,:address,
          :number,:complement,:district,:city,:state,:postal,:region,:locationUrl,:rules,:hours,:amenities,:capacity,:price,:payments,:now,:now)
          RETURNING public_id""", fieldParams(userId,r,city,state,now), UUID.class);
    }
    /** Atualiza somente se o usuario for o responsavel e a versao conferir; devolve as linhas alteradas. */
    public int updateField(UUID id, long userId, long version, SaveFieldRequest r, String city, String state, Instant now) {
        return jdbc.update("""
          UPDATE airsoft_field SET name=:name,description=:description,phone=:phone,contact_email=:email,address_line=:address,
          address_number=:number,complement=:complement,district=:district,city=:city,state_code=:state,postal_code=:postal,
          region=:region,location_url=:locationUrl,rules=:rules,opening_hours=:hours,amenities=:amenities,maximum_capacity=:capacity,
          average_price=:price,payment_methods=:payments,updated_at=:now,version=version+1
          WHERE public_id=:id AND responsible_user_id=:userId AND status='ACTIVE' AND version=:version""",
          fieldParams(userId,r,city,state,now).addValue("id",id).addValue("version",version));
    }
    public Optional<MapResponse> map(UUID id, long userId) {
        return jdbc.query("""
          SELECT m.*,f.public_id field_public_id,f.name field_name,f.city,f.state_code,f.responsible_user_id
          FROM field_map m JOIN airsoft_field f ON f.id=m.field_id WHERE m.public_id=:id AND m.status='ACTIVE' AND f.status='ACTIVE'""",
          Map.of("id",id),(r,i)->map(r,userId)).stream().findFirst();
    }
    public int updateMap(UUID id, long userId, long version, SaveMapRequest r, String terrain, Instant now) {
        return jdbc.update("""
          UPDATE field_map m SET name=:name,terrain_type=:terrain,description=:description,approximate_size=:size,capacity=:capacity,
          respawn_areas=:respawn,bases=:bases,objectives=:objectives,strategic_points=:strategic,neutral_areas=:neutral,
          prohibited_areas=:prohibited,routes=:routes,notes=:notes,specific_rules=:rules,updated_at=:now,version=m.version+1
          FROM airsoft_field f WHERE f.id=m.field_id AND m.public_id=:id AND m.status='ACTIVE' AND m.version=:version
          AND f.responsible_user_id=:userId AND f.status='ACTIVE'""",
          mapParams(r,terrain,now).addValue("id",id).addValue("userId",userId).addValue("version",version));
    }
    public List<MapResponse> maps(long userId, UUID fieldId, String terrain, String q, int limit) {
        return jdbc.query("""
          SELECT m.*,f.public_id field_public_id,f.name field_name,f.city,f.state_code,f.responsible_user_id
          FROM field_map m JOIN airsoft_field f ON f.id=m.field_id WHERE m.status='ACTIVE' AND f.status='ACTIVE'
          AND (CAST(:fieldId AS uuid) IS NULL OR f.public_id=:fieldId) AND (:terrain='' OR m.terrain_type=upper(:terrain))
          AND (:q='' OR lower(m.name) LIKE :query) ORDER BY lower(f.name),lower(m.name) LIMIT :limit""",
          new MapSqlParameterSource().addValue("fieldId",fieldId).addValue("terrain",terrain).addValue("q",q)
          .addValue("query","%"+q.toLowerCase()+"%").addValue("limit",limit),(r,i)->map(r,userId));
    }
    public UUID createMap(UUID fieldId, long userId, SaveMapRequest r, String terrain, Instant now) {
        return jdbc.queryForObject("""
          INSERT INTO field_map(field_id,name,terrain_type,description,approximate_size,capacity,respawn_areas,bases,
          objectives,strategic_points,neutral_areas,prohibited_areas,routes,notes,specific_rules,created_at,updated_at)
          SELECT id,:name,:terrain,:description,:size,:capacity,:respawn,:bases,:objectives,:strategic,:neutral,
          :prohibited,:routes,:notes,:rules,:now,:now FROM airsoft_field
          WHERE public_id=:fieldId AND responsible_user_id=:userId AND status='ACTIVE' RETURNING public_id""",
          mapParams(r,terrain,now).addValue("fieldId",fieldId).addValue("userId",userId),UUID.class);
    }
    private MapSqlParameterSource mapParams(SaveMapRequest r, String terrain, Instant now) {
        return new MapSqlParameterSource().addValue("name",norm(r.name()))
          .addValue("terrain",terrain).addValue("description",nullable(r.description())).addValue("size",nullable(r.approximateSize()))
          .addValue("capacity",r.capacity()).addValue("respawn",nullable(r.respawnAreas())).addValue("bases",nullable(r.bases()))
          .addValue("objectives",nullable(r.objectives())).addValue("strategic",nullable(r.strategicPoints()))
          .addValue("neutral",nullable(r.neutralAreas())).addValue("prohibited",nullable(r.prohibitedAreas()))
          .addValue("routes",nullable(r.routes())).addValue("notes",nullable(r.notes())).addValue("rules",nullable(r.specificRules()))
          .addValue("now",Timestamp.from(now));
    }
    private MapSqlParameterSource fieldParams(long userId, SaveFieldRequest r, String city, String state, Instant now) {
        return new MapSqlParameterSource().addValue("userId",userId).addValue("name",norm(r.name())).addValue("description",nullable(r.description()))
          .addValue("phone",nullable(r.phone())).addValue("email",nullable(r.contactEmail())).addValue("address",norm(r.addressLine()))
          .addValue("number",nullable(r.addressNumber())).addValue("complement",nullable(r.complement())).addValue("district",nullable(r.district()))
          .addValue("city",city).addValue("state",state).addValue("postal",nullable(r.postalCode())).addValue("region",nullable(r.region()))
          .addValue("locationUrl",nullable(r.locationUrl()))
          .addValue("rules",nullable(r.rules())).addValue("hours",nullable(r.openingHours())).addValue("amenities",nullable(r.amenities()))
          .addValue("capacity",r.maximumCapacity()).addValue("price",r.averagePrice()).addValue("payments",nullable(r.paymentMethods()))
          .addValue("now",Timestamp.from(now));
    }
    private FieldResponse field(ResultSet r,long userId)throws SQLException{return new FieldResponse(r.getObject("public_id",UUID.class),r.getString("name"),r.getString("description"),r.getString("phone"),r.getString("contact_email"),r.getString("address_line"),r.getString("address_number"),r.getString("complement"),r.getString("district"),r.getString("city"),r.getString("state_code"),r.getString("postal_code"),r.getString("region"),r.getString("location_url"),r.getString("rules"),r.getString("opening_hours"),r.getString("amenities"),(Integer)r.getObject("maximum_capacity"),r.getBigDecimal("average_price"),r.getString("payment_methods"),r.getLong("responsible_user_id")==userId,r.getLong("version"));}
    private MapResponse map(ResultSet r,long userId)throws SQLException{return new MapResponse(r.getObject("public_id",UUID.class),r.getObject("field_public_id",UUID.class),r.getString("field_name"),r.getString("city"),r.getString("state_code"),r.getString("name"),r.getString("terrain_type"),r.getString("description"),r.getString("approximate_size"),(Integer)r.getObject("capacity"),r.getString("respawn_areas"),r.getString("bases"),r.getString("objectives"),r.getString("strategic_points"),r.getString("neutral_areas"),r.getString("prohibited_areas"),r.getString("routes"),r.getString("notes"),r.getString("specific_rules"),r.getLong("responsible_user_id")==userId,r.getLong("version"));}
    private String norm(String v){return v==null?"":v.trim().replaceAll("\\s+"," ");} private String nullable(String v){String n=norm(v);return n.isEmpty()?null:n;}
}
