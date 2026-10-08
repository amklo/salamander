<?xml version='1.0' encoding='UTF-8'?>
<tileset version="1.10" tiledversion="1.10.2" name="objects" tilewidth="64" tileheight="168" tilecount="28" columns="0">
 <grid orientation="orthogonal" width="1" height="1" />
 <tile id="0" class="fan">
  <properties>
   <property name="count" type="int" value="5" />
   <property name="drop" type="bool" value="true" />
  </properties>
  <image source="icons/fan.png" width="16" height="16" />
 </tile>
 <tile id="1" class="rusher">
  <properties>
   <property name="count" type="int" value="3" />
   <property name="drop" type="bool" value="true" />
  </properties>
  <image source="icons/rusher.png" width="18" height="12" />
 </tile>
 <tile id="2" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="behind" type="bool" value="false" />
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
  <image source="../sprites/fang0.png" width="72" height="100" />
 </tile>
 <tile id="10" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang1.png" width="45" height="80" />
 </tile>
 <tile id="11" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang2.png" width="45" height="104" />
 </tile>
 <tile id="12" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang3.png" width="72" height="100" />
 </tile>
 <tile id="13" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang4.png" width="45" height="104" />
 </tile>
 <tile id="14" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false" />
   <property name="delay" type="float" value="1.0" />
   <property name="period" type="float" value="3.5" />
  </properties>
  <image source="../sprites/fang5.png" width="45" height="90" />
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
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="icons/bio_walker_blue.png" width="16" height="16"/>
 </tile>
 <tile id="24" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="behind" type="bool" value="false"/>
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
</tileset>