package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedClothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedClothesRepository
    extends JpaRepository<FeedClothes, FeedClothes.Key> {

  List<FeedClothes> findAllByFeed_Id(UUID feedId);
}
