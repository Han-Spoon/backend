package com.hanspoon.backend_api.domain.store.repository;

import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByIdAndStatus(Long id, StoreStatus status);

    /** 영업 중 가게 상세. 썸네일은 findThumbnailKeys. */
    @Query(
            value =
                    """
                    select s.id as "storeId",
                           s.name as "name",
                           s.branch_name as "branchName",
                           s.road_address as "roadAddress",
                           s.floor_info as "floorInfo",
                           s.lat as "latitude",
                           s.lng as "longitude",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (s.verified_at is not null) as "verified",
                           (select cast(count(*) as integer)
                              from meal_reviews r
                             where r.store_id = s.id
                               and r.hidden_at is null) as "reviewCount"
                      from stores s
                      left join store_categories c on c.id = s.category_id
                     where s.id = :storeId
                       and s.status = 'active'
                    """,
            nativeQuery = true)
    Optional<StoreDetailProjection> findActiveDetail(@Param("storeId") long storeId);

    /** 가게 대표 사진 키. 가게 사진, 없으면 사진 있는 첫 가게 메뉴(표시 순서 → id 순). 사진 없는 가게는 행 없음. */
    @Query(
            value =
                    """
                    select t."storeId", t."storageKey"
                      from (select s.id as "storeId",
                                   coalesce(
                                       (select ia.storage_key
                                          from image_assets ia
                                         where ia.id = s.image_asset_id
                                           and ia.status = 'active'),
                                       (select ia.storage_key
                                          from store_menus sm
                                          join image_assets ia on ia.id = sm.image_asset_id and ia.status = 'active'
                                         where sm.store_id = s.id
                                           and sm.status = 'active'
                                         order by sm.display_order nulls last, sm.id
                                         limit 1)
                                   ) as "storageKey"
                              from stores s
                             where s.id in (:storeIds)) t
                     where t."storageKey" is not null
                    """,
            nativeQuery = true)
    List<StoreThumbnailProjection> findThumbnailKeys(@Param("storeIds") Collection<Long> storeIds);

    /** 현재 위치 주변의 영업 중 가게를 검증 여부·공공데이터 출처·거리 순으로 조회. */
    @Query(
            value =
                    """
                    select s.id as "storeId",
                           s.name as "name",
                           s.branch_name as "branchName",
                           s.road_address as "roadAddress",
                           s.floor_info as "floorInfo",
                           s.lat as "latitude",
                           s.lng as "longitude",
                           cast(round(earth_distance(
                               ll_to_earth(:latitude, :longitude),
                               ll_to_earth(s.lat, s.lng)
                           )) as integer) as "distanceMeters",
                           cast(null as double precision) as "nameSimilarity",
                           cast(null as varchar) as "matchedMenuName",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (s.verified_at is not null) as "verified",
                           (select cast(count(*) as integer)
                              from meal_reviews r
                             where r.store_id = s.id
                               and r.hidden_at is null) as "reviewCount"
                      from stores s
                      left join store_categories c on c.id = s.category_id
                     where s.status = 'active'
                       and earth_box(
                               ll_to_earth(:latitude, :longitude),
                               :radiusMeters
                           ) @> ll_to_earth(s.lat, s.lng)
                       and earth_distance(
                               ll_to_earth(:latitude, :longitude),
                               ll_to_earth(s.lat, s.lng)
                           ) <= :radiusMeters
                     order by "verified" desc,
                              case s.origin
                                  when 'sbiz' then 0
                                  when 'localdata' then 1
                                  else 2
                              end,
                              "distanceMeters",
                              s.id
                     limit :limit
                    """,
            nativeQuery = true)
    List<StoreCandidateProjection> findNearbyCandidates(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit);

    /** 검색어 정규형. 매칭 규칙을 DB 함수 하나로 유지. */
    @Query(value = "select normalize_store_name(:query)", nativeQuery = true)
    String normalizeStoreName(@Param("query") String query);

    /**
     * 전국 상호명·메뉴명 검색(정규형 3글자 이상). 이름+지점명 포함 → 메뉴 포함 → 이름 유사 순, 같은 단계는 거리순.
     *
     * <p>좌표는 거리 계산·정렬에만 사용. 좌표가 없으면 거리 null, 일치 → 접두 → 포함 → 짧은 이름 순.
     * 이름과 메뉴에 함께 걸린 가게는 앞 단계 한 번만.
     */
    @Query(
            value =
                    """
                    with menu_hit as materialized (
                        select distinct on (sm.store_id) sm.store_id, sm.display_name
                          from store_menus sm
                         where sm.status = 'active'
                           and sm.name_normalized like '%' || :normalizedQuery || '%'
                         order by sm.store_id, char_length(sm.name_normalized), sm.id
                    ),
                    hit as materialized (
                        select s.id, s.name, s.branch_name, s.road_address, s.floor_info, s.lat, s.lng,
                               s.category_id, s.origin, s.verified_at, s.name_normalized,
                               case when s.branch_name = '' then s.name_normalized
                                    else normalize_store_name(s.name || s.branch_name)
                               end as full_name,
                               cast(null as varchar) as menu_name
                          from stores s
                         where s.status = 'active'
                           and (s.name_normalized like '%' || :normalizedQuery || '%'
                                or (s.branch_name <> ''
                                    and normalize_store_name(s.name || s.branch_name) like '%' || :normalizedQuery || '%')
                                or s.name_normalized % :normalizedQuery)
                        union all
                        select s.id, s.name, s.branch_name, s.road_address, s.floor_info, s.lat, s.lng,
                               s.category_id, s.origin, s.verified_at, s.name_normalized,
                               case when s.branch_name = '' then s.name_normalized
                                    else normalize_store_name(s.name || s.branch_name)
                               end as full_name,
                               m.display_name as menu_name
                          from menu_hit m
                          join stores s on s.id = m.store_id
                         where s.status = 'active'
                    ),
                    ranked as (
                        select distinct on (h.id) h.*,
                               case when h.full_name like '%' || :normalizedQuery || '%' then 0
                                    when h.menu_name is not null then 1
                                    else 2
                               end as tier
                          from hit h
                         order by h.id, tier
                    ),
                    top as (
                        select r.*,
                               row_number() over (
                                   order by r.verified_at is not null desc,
                                            r.tier,
                                            case when r.tier = 2 then similarity(r.name_normalized, :normalizedQuery) end desc nulls last,
                                            power(r.lat - cast(:latitude as double precision), 2)
                                                + power((r.lng - cast(:longitude as double precision))
                                                        * cos(radians((r.lat + cast(:latitude as double precision)) / 2)), 2) nulls last,
                                            case when r.name_normalized = :normalizedQuery or r.full_name = :normalizedQuery then 0
                                                 when r.full_name like :normalizedQuery || '%' then 1
                                                 else 2
                                            end,
                                            char_length(r.name_normalized),
                                            case r.origin
                                                when 'sbiz' then 0
                                                when 'localdata' then 1
                                                else 2
                                            end,
                                            r.id
                               ) as ord
                          from ranked r
                    )
                    select t.id as "storeId",
                           t.name as "name",
                           t.branch_name as "branchName",
                           t.road_address as "roadAddress",
                           t.floor_info as "floorInfo",
                           t.lat as "latitude",
                           t.lng as "longitude",
                           case when cast(:latitude as double precision) is null then null
                                else cast(round(earth_distance(
                                    ll_to_earth(cast(:latitude as double precision), cast(:longitude as double precision)),
                                    ll_to_earth(t.lat, t.lng)
                                )) as integer)
                           end as "distanceMeters",
                           similarity(t.name_normalized, :normalizedQuery) as "nameSimilarity",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (t.verified_at is not null) as "verified",
                           (select cast(count(*) as integer)
                              from meal_reviews mr
                             where mr.store_id = t.id
                               and mr.hidden_at is null) as "reviewCount",
                           case when t.tier = 1 then t.menu_name end as "matchedMenuName"
                      from top t
                      left join store_categories c on c.id = t.category_id
                     where t.ord <= :limit
                     order by t.ord
                    """,
            nativeQuery = true)
    List<StoreCandidateProjection> findCandidatesByName(
            @Param("normalizedQuery") String normalizedQuery,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("limit") int limit);

    /**
     * 짧은 검색어(정규형 1~2글자) 상호명·메뉴명 검색. trigram 이 없는 이름 포함 매칭은 반경 안으로 한정.
     *
     * <p>이름 매칭: 이름+지점명 전국 접두 일치 또는 반경 안 포함. 좌표가 없으면 전국 접두 일치만.
     * 메뉴 매칭: 메뉴명 포함(메뉴 적재 가게만). 이름 매칭 → 메뉴 매칭 순.
     */
    @Query(
            value =
                    """
                    with menu_hit as materialized (
                        select distinct on (sm.store_id) sm.store_id, sm.display_name
                          from store_menus sm
                         where sm.status = 'active'
                           and sm.name_normalized like '%' || :normalizedQuery || '%'
                         order by sm.store_id, char_length(sm.name_normalized), sm.id
                    ),
                    hit as materialized (
                        select s.id, s.name, s.branch_name, s.road_address, s.floor_info, s.lat, s.lng,
                               s.category_id, s.origin, s.verified_at, s.name_normalized,
                               case when s.branch_name = '' then s.name_normalized
                                    else normalize_store_name(s.name || s.branch_name)
                               end as full_name,
                               cast(null as varchar) as menu_name
                          from stores s
                         where s.status = 'active'
                           and (s.name_normalized like :normalizedQuery || '%'
                                or (s.branch_name <> ''
                                    and normalize_store_name(s.name || s.branch_name) like :normalizedQuery || '%'))
                        union all
                        select s.id, s.name, s.branch_name, s.road_address, s.floor_info, s.lat, s.lng,
                               s.category_id, s.origin, s.verified_at, s.name_normalized,
                               case when s.branch_name = '' then s.name_normalized
                                    else normalize_store_name(s.name || s.branch_name)
                               end as full_name,
                               cast(null as varchar) as menu_name
                          from stores s
                         where s.status = 'active'
                           and earth_box(
                                   ll_to_earth(cast(:latitude as double precision), cast(:longitude as double precision)),
                                   :radiusMeters
                               ) @> ll_to_earth(s.lat, s.lng)
                           and earth_distance(
                                   ll_to_earth(cast(:latitude as double precision), cast(:longitude as double precision)),
                                   ll_to_earth(s.lat, s.lng)
                               ) <= :radiusMeters
                           and (s.name_normalized like '%' || :normalizedQuery || '%'
                                or (s.branch_name <> ''
                                    and normalize_store_name(s.name || s.branch_name) like '%' || :normalizedQuery || '%'))
                        union all
                        select s.id, s.name, s.branch_name, s.road_address, s.floor_info, s.lat, s.lng,
                               s.category_id, s.origin, s.verified_at, s.name_normalized,
                               case when s.branch_name = '' then s.name_normalized
                                    else normalize_store_name(s.name || s.branch_name)
                               end as full_name,
                               m.display_name as menu_name
                          from menu_hit m
                          join stores s on s.id = m.store_id
                         where s.status = 'active'
                    ),
                    ranked as (
                        select distinct on (h.id) h.*,
                               case when h.menu_name is null then 0 else 1 end as tier
                          from hit h
                         order by h.id, tier
                    ),
                    top as (
                        select r.*,
                               row_number() over (
                                   order by r.verified_at is not null desc,
                                            r.tier,
                                            power(r.lat - cast(:latitude as double precision), 2)
                                                + power((r.lng - cast(:longitude as double precision))
                                                        * cos(radians((r.lat + cast(:latitude as double precision)) / 2)), 2) nulls last,
                                            case when r.name_normalized = :normalizedQuery or r.full_name = :normalizedQuery then 0
                                                 when r.full_name like :normalizedQuery || '%' then 1
                                                 else 2
                                            end,
                                            char_length(r.name_normalized),
                                            case r.origin
                                                when 'sbiz' then 0
                                                when 'localdata' then 1
                                                else 2
                                            end,
                                            r.id
                               ) as ord
                          from ranked r
                    )
                    select t.id as "storeId",
                           t.name as "name",
                           t.branch_name as "branchName",
                           t.road_address as "roadAddress",
                           t.floor_info as "floorInfo",
                           t.lat as "latitude",
                           t.lng as "longitude",
                           case when cast(:latitude as double precision) is null then null
                                else cast(round(earth_distance(
                                    ll_to_earth(cast(:latitude as double precision), cast(:longitude as double precision)),
                                    ll_to_earth(t.lat, t.lng)
                                )) as integer)
                           end as "distanceMeters",
                           similarity(t.name_normalized, :normalizedQuery) as "nameSimilarity",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (t.verified_at is not null) as "verified",
                           (select cast(count(*) as integer)
                              from meal_reviews mr
                             where mr.store_id = t.id
                               and mr.hidden_at is null) as "reviewCount",
                           case when t.tier = 1 then t.menu_name end as "matchedMenuName"
                      from top t
                      left join store_categories c on c.id = t.category_id
                     where t.ord <= :limit
                     order by t.ord
                    """,
            nativeQuery = true)
    List<StoreCandidateProjection> findCandidatesByShortName(
            @Param("normalizedQuery") String normalizedQuery,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit);
}
