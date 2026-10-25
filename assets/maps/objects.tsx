<?xml version='1.0' encoding='UTF-8'?>
<tileset version="1.10" tiledversion="1.10.2" name="objects" tilewidth="64" tileheight="168" tilecount="60" columns="0">
 <grid orientation="orthogonal" width="1" height="1" />
 <tile id="2" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="behind" type="bool" value="false" />
   <property name="leap" value="" />
   <property name="drop" type="bool" value="true" />
  </properties>
  <image source="icons/walker.png" width="16" height="16" />
 </tile>
 <tile id="3" class="turret">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="drop" type="bool" value="false" />
  </properties>
  <image source="icons/bio_turret_blue.png" width="16" height="16" />
 </tile>
 <tile id="4" class="bamda">
  <image source="icons/bamda.png" width="30" height="29" />
 </tile>
 <tile id="5" class="bamda">
  <image source="icons/bamda.png" width="30" height="29" />
 </tile>
 <tile id="6" class="rock">
  <image source="../sprites/rock.png" width="16" height="16" />
 </tile>
 <tile id="7" class="volcano">
  <image source="icons/volcano.png" width="12" height="6" />
 </tile>
 <tile id="8" class="crusher">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="phase" type="float" value="0.0" />
   <property name="minLen" type="float" value="12.0" />
   <property name="maxLen" type="float" value="102.0" />
   <property name="speed" type="float" value="1.6" />
  </properties>
  <image source="../sprites/crusher_head.png" width="32" height="16" />
 </tile>
 <tile id="9" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="10" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="11" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="12" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="13" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="14" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang full.png" width="45" height="160"/>
 </tile>
 <tile id="15" class="arm">
  <properties>
   <property name="ceiling" type="bool" value="false" />
  </properties>
  <image source="icons/arm.png" width="64" height="40" />
 </tile>
 <tile id="16" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
  </properties>
  <image source="../sprites/tower0.png" width="16" height="38"/>
 </tile>
 <tile id="17" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
  </properties>
  <image source="../sprites/tower1.png" width="16" height="45"/>
 </tile>
 <tile id="18" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
  </properties>
  <image source="../sprites/tower2.png" width="16" height="39"/>
 </tile>
 <tile id="19" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
  </properties>
  <image source="../sprites/tower3.png" width="16" height="23"/>
 </tile>
 <tile id="20" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
  </properties>
  <image source="../sprites/tower4.png" width="16" height="23"/>
 </tile>
 <tile id="21" class="tower">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
  </properties>
  <image source="../sprites/tower5.png" width="16" height="40"/>
 </tile>
 <tile id="22" class="turret">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/bio_turret_red.png" width="16" height="16"/>
 </tile>
 <tile id="23" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="behind" type="bool" value="false"/>
   <property name="leap" value=""/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/bio_walker_blue.png" width="16" height="16"/>
 </tile>
 <tile id="24" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="behind" type="bool" value="false"/>
   <property name="leap" value=""/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/bio_walker_red.png" width="16" height="16"/>
 </tile>
 <tile id="25" class="frost">
  <properties>
   <property name="count" type="int" value="6"/>
   <property name="drop" type="bool" value="true"/>
   <property name="upper" type="bool" value="false"/>
  </properties>
  <image source="icons/celtic_frost.png" width="13" height="15"/>
 </tile>
 <tile id="26" class="link">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/missing_link.png" width="16" height="16"/>
 </tile>
 <tile id="27" class="rugal">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/rugal.png" width="16" height="16"/>
 </tile>
 <tile id="28" class="wallturret">
  <properties>
   <property name="drop" type="bool" value="false"/>
   <property name="diag" type="bool" value="false"/>
  </properties>
  <image source="icons/wall_turret_green.png" width="16" height="16"/>
 </tile>
 <tile id="29" class="wallturret">
  <properties>
   <property name="drop" type="bool" value="true"/>
   <property name="diag" type="bool" value="false"/>
  </properties>
  <image source="icons/wall_turret_red.png" width="16" height="16"/>
 </tile>
 <tile id="30" class="dome">
  <image source="icons/dome.png" width="18" height="32"/>
 </tile>
 <tile id="31" class="eye">
  <image source="icons/rock_eye.png" width="32" height="32"/>
 </tile>
 <tile id="32" class="legacy">
  <properties>
   <property name="count" type="int" value="4"/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/legacy.png" width="16" height="16"/>
 </tile>
 <tile id="33" class="rugal">
  <properties>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/rugal_red.png" width="16" height="16"/>
 </tile>
 <tile id="34" class="boss">
  <image source="icons/boss.png" width="16" height="16"/>
 </tile>
 <tile id="35" class="omen">
  <image source="../sprites/Omen.png" width="32" height="16"/>
 </tile>
 <tile id="36" class="floordome">
  <image source="icons/floordome.png" width="32" height="18"/>
 </tile>
 <tile id="37" class="gunho">
  <image source="../sprites/Gun Ho.png" width="26" height="34"/>
 </tile>
 <tile id="38" class="laserguard">
  <image source="../sprites/Laser Guard.png" width="22" height="20"/>
 </tile>
 <tile id="39" class="laserguard_wall">
  <image source="icons/laserguard_wall.png" width="20" height="22"/>
 </tile>
 <tile id="40" class="spark">
  <image source="../sprites/spark.png" width="16" height="16"/>
 </tile>
 <tile id="41" class="laser">
  <image source="icons/laser.png" width="16" height="4"/>
 </tile>
 <tile id="42" class="aberrant">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/aberrant.png" width="16" height="16"/>
 </tile>
 <tile id="43" class="nest">
  <properties>
   <property name="spawns" value="ug"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/nest.png" width="38" height="22"/>
 </tile>
 <tile id="44" class="chain">
  <properties>
   <property name="count" type="int" value="7"/>
  </properties>
  <image source="../sprites/ball.png" width="12" height="12"/>
 </tile>
 <tile id="45" class="seed">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/eioneus_base.png" width="44" height="46"/>
 </tile>
 <tile id="46" class="bluedome">
  <image source="../sprites/bluedome.png" width="14" height="38"/>
 </tile>
 <tile id="47" class="cluster">
  <image source="../sprites/cluster.png" width="32" height="31"/>
 </tile>
 <tile id="48" class="mine">
  <image source="../sprites/mine.png" width="16" height="18"/>
 </tile>
 <tile id="49" class="crab">
  <image source="../sprites/crab.png" width="50" height="36"/>
 </tile>
 <tile id="50" class="gunpod">
  <image source="../sprites/gunpod.png" width="42" height="74"/>
 </tile>
 <tile id="51" class="orb">
  <image source="../sprites/orb.png" width="18" height="18"/>
 </tile>
 <tile id="52" class="vent">
  <image source="../sprites/vent_red.png" width="16" height="10"/>
 </tile>
 <tile id="53" class="coregate">
  <image source="../sprites/coregate.png" width="64" height="51"/>
 </tile>
 <tile id="54" class="meteors">
  <properties>
   <property name="time" type="float" value="15" />
  </properties>
  <image source="../sprites/rock_big.png" width="30" height="27"/>
 </tile>
 <tile id="55" class="possessed">
  <properties>
   <property name="count" type="int" value="4"/>
   <property name="reds" type="int" value="2"/>
   <property name="drop" type="bool" value="true"/>
   <property name="row" type="bool" value="false"/>
  </properties>
  <image source="../sprites/possessed_red.png" width="16" height="16"/>
 </tile>
 <tile id="56" class="whiplash">
  <properties>
   <property name="fromBottom" type="bool" value="false"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/whiplash.png" width="15" height="16"/>
 </tile>
 <tile id="57" class="battleaxe">
  <properties>
   <property name="count" type="int" value="6"/>
   <property name="high" type="bool" value="false"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/battleaxe.png" width="16" height="16"/>
 </tile>
 <tile id="58" class="gamma">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/gamma.png" width="16" height="16"/>
 </tile>
 <tile id="59" class="laserbattery">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/laser_battery_small.png" width="24" height="16"/>
 </tile>
 <tile id="60" class="laserbattery_big">
  <properties>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/laser_battery_big.png" width="32" height="16"/>
 </tile>
 <tile id="61" class="gremlin">
  <properties>
   <property name="red" type="bool" value="false"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/gremlin.png" width="16" height="16"/>
 </tile>
</tileset>
