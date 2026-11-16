package com.hanspoon.backend_api.domain.store.repository;

import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByIdAndStatus(Long id, StoreStatus status);

    /** 현재 위치 주변의 영업 중 가게를 검증 여부·공공데이터 출처·거리 순으로 조회. */
    @Query(
            value =
                    """
                    select s.id as "storeId",
                           s.name as "name",
                           s.branch_name as "branchName",
                           s.road_address as "roadAddress",
                           s.lat as "latitude",
                           s.lng as "longitude",
                           cast(round(earth_distance(
                               ll_to_earth(:latitude, :longitude),
                               ll_to_earth(s.lat, s.lng)
                           )) as integer) as "distanceMeters",
                           cast(null as double precision) as "nameSimilarity",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (s.verified_at is not null) as "verified"
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

    /** 정규화된 상호명 trigram 후보를 현재 위치 반경 안에서 조회한다. */
    @Query(
            value =
                    """
                    select s.id as "storeId",
                           s.name as "name",
                           s.branch_name as "branchName",
                           s.road_address as "roadAddress",
                           s.lat as "latitude",
                           s.lng as "longitude",
                           cast(round(earth_distance(
                               ll_to_earth(:latitude, :longitude),
                               ll_to_earth(s.lat, s.lng)
                           )) as integer) as "distanceMeters",
                           similarity(s.name_normalized, normalize_store_name(:query)) as "nameSimilarity",
                           c.code as "categoryCode",
                           c.name as "categoryName",
                           (s.verified_at is not null) as "verified"
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
                       and s.name_normalized % normalize_store_name(:query)
                     order by "verified" desc,
                              case s.origin
                                  when 'sbiz' then 0
                                  when 'localdata' then 1
                                  else 2
                              end,
                              "nameSimilarity" desc,
                              "distanceMeters",
                              s.id
                     limit :limit
                    """,
            nativeQuery = true)
    List<StoreCandidateProjection> findNearbyCandidatesByName(
            @Param("query") String query,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit);
}
