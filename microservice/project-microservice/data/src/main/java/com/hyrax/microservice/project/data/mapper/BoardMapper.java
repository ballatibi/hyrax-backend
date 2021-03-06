package com.hyrax.microservice.project.data.mapper;

import com.hyrax.microservice.project.data.entity.BoardEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BoardMapper {

    BoardEntity selectByBoardName(@Param("boardName") String boardName);

    List<BoardEntity> selectAllBoardByUsername(@Param("username") String username);

    void insert(@Param("boardName") String boardName, @Param("ownerUsername") String ownerUsername);

    void delete(@Param("boardName") String boardName);
}
