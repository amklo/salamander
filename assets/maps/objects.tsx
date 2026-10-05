<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.10.2" name="objects" tilewidth="64" tileheight="168" tilecount="15" columns="0">
 <grid orientation="orthogonal" width="1" height="1"/>
 <tile id="0" class="fan">
  <properties>
   <property name="count" type="int" value="5"/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/fan.png" width="16" height="16"/>
 </tile>
 <tile id="1" class="rusher">
  <properties>
   <property name="count" type="int" value="3"/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/rusher.png" width="18" height="12"/>
 </tile>
 <tile id="2" class="walker">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="behind" type="bool" value="false"/>
   <property name="drop" type="bool" value="true"/>
  </properties>
  <image source="icons/walker.png" width="16" height="16"/>
 </tile>
 <tile id="3" class="turret">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="drop" type="bool" value="false"/>
  </properties>
  <image source="../sprites/turret.png" width="16" height="16"/>
 </tile>
 <tile id="4" class="asteroid">
  <image source="../sprites/asteroid_big.png" width="36" height="37"/>
 </tile>
 <tile id="5" class="asteroid">
  <image source="../sprites/asteroid_small.png" width="23" height="23"/>
 </tile>
 <tile id="6" class="rock">
  <image source="../sprites/rock.png" width="16" height="16"/>
 </tile>
 <tile id="7" class="volcano">
  <image source="icons/volcano.png" width="12" height="6"/>
 </tile>
 <tile id="8" class="crusher">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
   <property name="phase" type="float" value="0.0"/>
   <property name="minLen" type="float" value="12.0"/>
   <property name="maxLen" type="float" value="102.0"/>
   <property name="speed" type="float" value="1.6"/>
  </properties>
  <image source="../sprites/crusher_head.png" width="32" height="16"/>
 </tile>
 <tile id="9" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang0.png" width="117" height="162"/>
 </tile>
 <tile id="10" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang1.png" width="73" height="130"/>
 </tile>
 <tile id="11" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang2.png" width="73" height="168"/>
 </tile>
 <tile id="12" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang3.png" width="117" height="162"/>
 </tile>
 <tile id="13" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="true"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang4.png" width="73" height="168"/>
 </tile>
 <tile id="14" class="tooth">
  <properties>
   <property name="ceiling" type="bool" value="false"/>
   <property name="delay" type="float" value="1.0"/>
   <property name="period" type="float" value="3.5"/>
  </properties>
  <image source="../sprites/fang5.png" width="73" height="146"/>
 </tile>
</tileset>
