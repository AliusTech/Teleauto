import 'package:flutter/material.dart';
import 'package:teleauto_core/teleauto_core.dart';
void main()=>runApp(MaterialApp(home:Scaffold(body:FutureBuilder(future:const TeleautoCore().state(),builder:(context,state)=>Center(child:Text('${state.data??state.error??"Loading"}'))))));
